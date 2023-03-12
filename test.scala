//> using lib "ch.epfl.scala::tasty-query:0.6.1"
//> using lib "org.playframework.anorm::anorm:2.7.0"
//> using lib "com.h2database:h2:2.1.214"
//> using lib "com.lihaoyi::os-lib:0.9.1"
//> using lib "com.lihaoyi::pprint:0.8.1"

import anorm.ParameterValue
import org.h2.tools.Server
import resource.Using
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
import tastyquery.jdk.ClasspathLoaders

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.sql.Connection
import java.sql.DriverManager
import scala.jdk.CollectionConverters.*
import scala.reflect.ClassTag
import scala.reflect.TypeTest
import tastyquery.Annotations.Annotation

@main def hello =
  // val classpath = System.getProperty("java.class.path")

  // val fetched = classpath.split(File.pathSeparator).toList.map(Paths.get(_))

  val fetched = os
    .proc("cs fetch com.indoorvivants.roach:core_native0.4_3:0.0.2".split(" "))
    .call()
    .out
    .lines()
    .toList
    .map(Paths.get(_))
  val cp = ClasspathLoaders.read(fetched)
  given ctx: Context = Contexts.init(cp)

  val url = "jdbc:h2:mem:./test"

  scala.util.Using.resource(
    DriverManager.getConnection(url, "sa", "")
  ) { conn =>
    given Connection = conn

    indexPackage(ctx.defn.RootPackage)
    org.h2.tools.Shell.main("-url", url, "-user", "sa")
  }

end hello

def indexPackage(pkg: PackageSymbol)(using Connection, Context): Unit =
  val builder = classBuilder.reference(_ => pkg).build
  val defs = pkg.declarations.only[ClassSymbol]
  populate(builder, defs)
  loggy {
    pkg.declarations.only[PackageSymbol].foreach { pkg =>
      loggyN(s"Indexing ${pkg}") {
        indexPackage(pkg)
      }
    }
  }
end indexPackage

def populate[T: HasId](indexer: Indexer[T], values: Iterable[T])(using
    Connection
) =
  import anorm.*
  val hi = summon[HasId[T]]
  val schema = s"create table if not exists ${hi.relName} ${indexer.fields
      .map {

        case Field(nm, FieldType.S) =>
          s"$nm VARCHAR"
        case Field(nm, FieldType.B) =>
          s"$nm BOOLEAN"
      }
      .mkString("(", ", ", ")")}"

  SQL(schema).execute()
  val namesList = indexer.fields.map(_.name).mkString(", ")
  val valuesList = indexer.fields.map("{" + _.name + "}").mkString(", ")

  values.foreach { v =>
    loggyN(s"inserting $v") {
      val row = indexer.go(v)
      val n = indexer.fields.map(n =>
        NamedParameter(n.name, row(n).map(_.asParameterValue).orNull)
      )
      println(row)
      val query = s"insert into ${hi.relName}($namesList) values($valuesList)"
      println(query)
      println(n)
      SQL(query)
        .on(n*)
        .execute()
    }
  }
end populate

def classId(symb: ClassSymbol) =
  symb.formatted
  val pkgHash = symb.fullName.path.dropRight(1).hashCode()
  s"${pkgHash}:${symb.name.toDebugString}"

case class Field(name: String, tpe: FieldType)

opaque type ClassAnnotaion = (ClassSymbol, Annotation)

given HasId[ClassSymbol] with
  def entityName: String = "class"
  def identify(cls: ClassSymbol) = classId(cls)

given HasId[PackageSymbol] with
  def entityName: String = "package"
  def identify(cls: PackageSymbol) = cls.fullName.toDebugString

def classBuilder(using Context) =
  Builder[ClassSymbol]
    .selfReference("companion_class", _.companionClass.orNull)
    .storeStr("name", _.name.toString)
    .storeBool("is_abstract", _.is(Flags.Abstract))
    .storeBool("is_case", _.is(Flags.Case))
    .storeBool("is_private", _.is(Flags.Private))
    .storeBool("is_sealed", _.is(Flags.Private))
    .storeBool("is_trait", _.is(Flags.Trait))
    .storeBool("is_class", _.isClass)

extension [A](collection: List[A])
  def only[C: ClassTag](using C <:< A): List[C] = collection.collect {
    case a: C =>
      a
  }

inline def loggyN[T](name: String)(e: => T): Option[T] =
  try
    val res = Some(e)
    println(s"[$name ✅]")
    res
  catch
    case exc =>
      println(s"[$name ❌]" + exc.getMessage())
      None

inline def loggy(e: => Unit) =
  try e
  catch case exc => println("ERROR: " + exc.getMessage())
