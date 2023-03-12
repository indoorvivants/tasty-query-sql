//> using lib "ch.epfl.scala::tasty-query:0.6.1"
//> using lib "org.playframework.anorm::anorm:2.7.0"
//> using lib "com.h2database:h2:2.1.214"
//> using lib "com.lihaoyi::os-lib:0.9.1"
//> using lib "com.lihaoyi::pprint:0.8.1"
//> using lib "com.lihaoyi::mainargs:0.4.0"

import annotations.ClassAnnotation
import anorm.ParameterValue
import org.h2.tools.Server
import resource.Using
import tastyquery.Annotations.Annotation
import tastyquery.Classpaths.Classpath
import tastyquery.Contexts
import tastyquery.Contexts.Context
import tastyquery.Flags
import tastyquery.Symbols.ClassSymbol
import tastyquery.Symbols.ClassTypeParamSymbol
import tastyquery.Symbols.LocalTypeParamSymbol
import tastyquery.Symbols.PackageSymbol
import tastyquery.Symbols.TermSymbol
import tastyquery.Symbols.TypeMemberSymbol
import tastyquery.Trees.DefDef
import tastyquery.Trees.ValDef
import tastyquery.jdk.ClasspathLoaders

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.sql.Connection
import java.sql.DriverManager
import scala.jdk.CollectionConverters.*
import scala.reflect.ClassTag
import scala.reflect.TypeTest

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

  val cp = ClasspathLoaders.read(fetched)
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

def populate[T: HasId](indexer: Indexer[T], values: Iterable[T])(using
    Connection
) =
  import anorm.*
  if values.nonEmpty then
    val hi = summon[HasId[T]]
    val schema = s"create table if not exists ${hi.relName} ${indexer.fields
        .map {

          case Field(nm, FieldType.S) =>
            s"$nm VARCHAR"
          case Field(nm, FieldType.B) =>
            s"$nm BOOLEAN"
          case Field(nm, FieldType.I) =>
            s"$nm INTEGER"
        }
        .mkString("(", ", ", ")")}"

    SQL(schema).execute()

    values.foreach { v =>
      loggyN(s"inserting $v") {
        val row = indexer.go(v)
        val nonNull = row.filter(_._2.nonEmpty).map(_._1).toList
        val namesList = nonNull.map(_.name).mkString(", ")
        val valuesList = nonNull.map("{" + _.name + "}").mkString(", ")
        val n = nonNull.map(n =>
          NamedParameter(n.name, row(n).map(_.asParameterValue).orNull)
        )
        val query = s"insert into ${hi.relName}($namesList) values($valuesList)"
        SQL(query)
          .on(n*)
          .execute()
      }
    }
  end if
end populate

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

def defdefBuilder(using Context) =
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

def annotationBuilder(using Context) =
  Builder[Annotation]
    .reference(_.symbol.as[ClassSymbol].orNull)
    .storeInt("arg_count", _.argCount)

extension [A](collection: List[A])
  def only[C: ClassTag](using C <:< A): List[C] = collection.collect {
    case a: C =>
      a
  }

extension [A](value: A)
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
