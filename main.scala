//> using lib "ch.epfl.scala::tasty-query:0.6.1"
//> using lib "org.playframework.anorm::anorm:2.7.0"
//> using lib "com.h2database:h2:2.1.214"
//> using lib "com.lihaoyi::os-lib:0.9.1"
//> using lib "com.lihaoyi::pprint:0.8.1"
//> using lib "com.lihaoyi::mainargs:0.4.0"
//> using scala "3.3.0-RC3"
//> using option "-Wunused:all"

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

object Main:
  import mainargs.*
  @mainargs.main
  def run(
      @arg(short = 'c', doc = "Classpath. Default: current JVM's classpath")
      classpath: String = System.getProperty("java.class.path"),
      @arg(doc = "Start H2 server and console")
      start: Flag = Flag(false)
  ) =
    server(classpath, start.value)
  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
end Main

def server(classpath: String, start: Boolean) =
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
  given ctx: Context = Contexts.init(cp)

  val url = "jdbc:h2:mem:./test"

  scala.util.Using.resource(
    DriverManager.getConnection(url, "sa", "")
  ) { conn =>
    given Connection = conn

    // Index classes
    forEachPackage { pkg =>
      val builder = classBuilder.reference(_ => pkg).build

      loggy {
        populate(builder, pkg.declarations.only[ClassSymbol])
      }
    }

    val builder = annotationBuilder.build

    forEachClass { cls =>
      loggyN(s"annotations of $cls") {
        val annots = cls.annotations
        populate(builder, annots)
      }

      loggyN(s"methods of $cls") {
        val methods = cls.declarations.flatMap(_.tree).flatMap(_.as[DefDef])
        populate(defdefBuilder.build, methods)
      }

      loggyN(s"parents of $cls") {
        populate(classHierarchyBuilder.build, cls.parentClasses.map(cls -> _))
      }

    }

    if start then org.h2.tools.Server.startWebServer(conn)

  }
end server

def forEachPackage(f: PackageSymbol => Unit)(using ctx: Context) =
  def go(pkg: PackageSymbol): Unit =
    pkg.declarations.only[PackageSymbol].foreach { p =>
      f(p)
      go(p)
    }
  go(ctx.defn.RootPackage)

def forEachClass(cls: ClassSymbol => Unit)(using Context) =
  forEachPackage { pkg =>
    pkg.declarations.only[ClassSymbol].foreach(cls)
  }

def classId(symb: ClassSymbol) =
  symb.formatted
  val pkgHash = symb.fullName.path.dropRight(1).hashCode()
  s"${pkgHash}:${symb.name.toDebugString}"

case class Field(name: String, tpe: FieldType)

object annotations:
  opaque type ClassAnnotation <: Annotation = Annotation
  object ClassAnnotation:
    def apply(s: Annotation): ClassAnnotation = s
    given (using Context): HasId[ClassAnnotation] with
      def entityName: String = "class_annotation"
      def identify(ca: ClassAnnotation) =
        classId(ca.symbol)

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

given HasId[DefDef] with
  def entityName = "method"
  def identify(v: DefDef): String = v.name.toDebugString

def defdefBuilder =
  Builder[DefDef]
    .reference(_.symbol.enclosingDecl.as[ClassSymbol].orNull)
    .storeStr("name", _.name.toString())
    .storeBool("is_abstract", _.symbol.is(Flags.Abstract))
    .storeBool("is_private", _.symbol.is(Flags.Private))
    .storeBool("is_protected", _.symbol.is(Flags.Protected))
    .storeBool("is_override", _.symbol.is(Flags.Override))
    .storeBool("is_inline", _.symbol.is(Flags.Inline))
    .storeBool("is_infix", _.symbol.is(Flags.Infix))
    .storeBool("is_final", _.symbol.is(Flags.Final))
    .storeBool("is_extension", _.symbol.is(Flags.Extension))

def classBuilder(using Context) =
  Builder[ClassSymbol]
    .selfReference("companion_class", _.companionClass.orNull)
    .storeStr("name", _.name.toString)
    .storeBool("is_abstract", _.is(Flags.Abstract))
    .storeBool("is_case", _.is(Flags.Case))
    .storeBool("is_private", _.is(Flags.Private))
    .storeBool("is_sealed", _.is(Flags.Private))
    .storeBool("is_trait", _.is(Flags.Trait))
    .storeBool("is_module", _.is(Flags.Module))
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
  try
    val res = Some(e)
    // println(s"[$name ✅]")
    res
  catch
    case exc =>
      println(s"[$name ❌]" + exc.getMessage())
      None

inline def loggy(e: => Unit) =
  try e
  catch case exc => println("ERROR: " + exc.getMessage())
