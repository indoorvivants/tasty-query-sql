import org.h2.tools.Server
import tastyquery.Annotations.Annotation
import tastyquery.Contexts
import tastyquery.Contexts.Context
import tastyquery.Flags
import tastyquery.Symbols.ClassSymbol
import tastyquery.Symbols.PackageSymbol

import java.io.File
import java.nio.file.Paths
import java.sql.Connection
import java.sql.DriverManager
import scala.reflect.ClassTag
import tastyquery.jdk.ClasspathLoaders
import tastyquery.Trees.DefDef
import scala.annotation.nowarn
import java.nio.file.FileSystems
import java.net.URI
import scala.io.StdIn
import scala.util.control.NoStackTrace

object Main:
  import mainargs.*
  @mainargs.main
  def run(
      @arg(short = 'c', doc = "Classpath. Default: current JVM's classpath")
      classpath: String = System.getProperty("java.class.path"),
      @arg(doc = "Start H2 web console")
      web: Flag = Flag(false),
      @arg(doc = "Start H2 server ")
      server: Flag = Flag(false)
  ) =
    start(classpath, web.value, server.value)
  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
end Main

def start(classpath: String, web: Boolean, server: Boolean) =
  val fetched = classpath.split(File.pathSeparator).toList.map(Paths.get(_))
  val javaLib =
    // post java 9
    def fromJRT = FileSystems
      .getFileSystem(URI.create("jrt:/"))
      .getPath("modules/java.base")
    // pre java 9
    def fromBootCP =
      for
        bootClasspath <- Option(System.getProperty("sun.boot.class.path"))
        rtJars <- Some(
          bootClasspath.split(java.io.File.pathSeparatorChar).toList.filter {
            path =>
              path.endsWith("rt.jar") || path.endsWith("jce.jar") // crypto
          }
        )
      yield rtJars.map(Paths.get(_))
    fromBootCP.getOrElse(List(fromJRT))
  end javaLib

  val cp = ClasspathLoaders.read(fetched ++ javaLib)
  given ctx: Context = Contexts.Context.initialize(cp)

  val runningServer =
    if server then
      Some(
        org.h2.tools.Server.createTcpServer("-tcpAllowOthers", "-ifNotExists")
      )
    else None

  runningServer.map(_.start())

  val url = "jdbc:h2:mem:db1"

  println(url)

  scala.util.Using.resource(
    DriverManager.getConnection(url, "sa", "")
  ) { conn =>
    given Connection = conn

    locally:
        import anorm.*

        SQL("SET REFERENTIAL_INTEGRITY FALSE").execute()

    val allPackages = collector(forEachPackage)

    populate(packageBuilder.build, allPackages)

    // Index classes
    allPackages.foreach { pkg =>
      val builder = classBuilder.reference(_ => pkg).build

      loggy {
        populate(builder, pkg.declarations.only[ClassSymbol])
      }
    }

    val builder = annotationBuilder.build

    forEachClass { cls =>
      // loggyN(s"annotations of $cls") {
      //   val annots = cls.annotations
      //   populate(builder, annots)
      // }

      loggyN(s"methods of $cls") {
        val methods = cls.declarations.flatMap(_.tree).flatMap(_.as[DefDef])
        populate(defdefBuilder(cls).build, methods)
      }

      loggyN(s"parents of $cls") {
        populate(classHierarchyBuilder.build, cls.parentClasses.map(cls -> _))
      }

    }

    if server then StdIn.readLine()
    if web then org.h2.tools.Server.startWebServer(conn)

  }
  runningServer.map(_.stop())
end start

def collector[A](f: (A => Unit) => Unit) =
  val b = Vector.newBuilder[A]

  f(a => b += a)

  b.result()

def forEachPackage(f: PackageSymbol => Unit)(using ctx: Context) =
  def go(pkg: PackageSymbol): Unit =
    loggyN(s"recursing into $pkg") {
      pkg.declarations.only[PackageSymbol].foreach { p =>
        f(p)
        go(p)
      }
    }
  go(ctx.defn.RootPackage)
end forEachPackage

def forEachClass(cls: ClassSymbol => Unit)(using Context) =
  forEachPackage { pkg =>
    pkg.declarations.only[ClassSymbol].foreach(cls)
  }

def classId(symb: ClassSymbol) =
  val pkgHash = symb.name.hashCode()
  s"${pkgHash}:${symb.name.toDebugString}"

def methodId(symb: DefDef)(using Context) =
  val declaring = symb.symbol.owner.as[ClassSymbol].map(classId)
  // symb.symbol.staticRef.name.toDebugString
  declaring
    .map(
      _ + "/" + symb.name.toDebugString + symb.symbol.signature.toDebugString
    )
    .getOrElse(
      err(s"Method ${symb.name} has no declaring class")
    )
end methodId

def err(msg: String) = throw new RuntimeException(msg) with NoStackTrace

case class ForeignKey(rel: String, field: String)

case class Field(
    name: String,
    tpe: FieldType,
    primaryKey: Boolean = false,
    foreignKey: Option[ForeignKey] = None
)

given HasId[ClassSymbol] with
  def entityName: String = "class"
  def identify(cls: ClassSymbol) = classId(cls)

given HasId[PackageSymbol] with
  def entityName: String = "package"
  def identify(cls: PackageSymbol) = cls.fullName.toDebugString

given (using Context): HasId[Annotation] with
  def entityName: String = "annotation"
  def identify(cls: Annotation) =
    summon[HasId[ClassSymbol]].identify(cls.symbol)

given (using Context): HasId[DefDef] with
  def entityName = "method"
  def identify(v: DefDef): String = methodId(v)

def defdefBuilder(cls: ClassSymbol)(using Context) =
  Builder[DefDef]
    .reference(_ => cls)
    .storeStr("name", _.name.toString())
    // .storeBool("is_abstract", _.symbol.owner.isAbstractClass)
    .storeBool("is_private", _.symbol.isPrivate)
  // .storeBool("is_protected", _.symbol.isProtected)
  // .storeBool("is_override", _.symbol.is(Flags.Override))
  // .storeBool("is_inline", _.symbol.is(Flags.Inline))
  // .storeBool("is_infix", _.symbol.is(Flags.Infix))
  // .storeBool("is_final", _.symbol.is(Flags.Final))
  // .storeBool("is_extension", _.symbol.is(Flags.Extension))

def packageBuilder(using Context) =
  Builder[PackageSymbol]

def classBuilder(using Context) =
  Builder[ClassSymbol]
    .selfReference("companion_class", _.companionClass.orNull)
    .storeStr("name", _.name.toString)
    .storeBool("is_abstract", _.isAbstractClass)
    .storeBool("is_case", _.isCaseClass)
    // .storeBool("is_private", _.is(Flags.Private))
    // .storeBool("is_sealed", _.is(Flags.Private))
    // .storeBool("is_trait", _.is(Flags.Trait))
    // .storeBool("is_module", _.is(Flags.Module))
    .storeBool("is_class", _.isClass)
end classBuilder

def classHierarchyBuilder =
  RelBuilder[(ClassSymbol, ClassSymbol)]("class_parents")
    .reference[ClassSymbol, ClassSymbol](
      "class" -> (_._1),
      "parent" -> (_._2)
    )

def annotationBuilder(using Context) =
  Builder[Annotation]
    .reference(_.symbol.as[ClassSymbol].orNull)
    .storeInt("arg_count", _.argCount)

extension [A](collection: List[A])
  @nowarn
  def only[C: ClassTag](using C <:< A): List[C] = collection.collect {
    case a: C =>
      a
  }

extension [A](value: A)
  @nowarn
  def as[C: ClassTag](using C <:< A): Option[C] = value match
    case a: C =>
      Some(a)
    case _ => None

inline def loggyN[T](name: String)(e: => T): Option[T] =
  try Some(e)
  catch
    case exc =>
      scribe.error(s"$name", exc.getMessage())
      None

inline def loggy(e: => Unit) =
  try e
  catch case exc => scribe.error(exc.getMessage())
