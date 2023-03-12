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

class Builder[T: HasId]:
  private val b = Vector.newBuilder[BuildAction[T]]
  private val schema = collection.mutable.Set.empty[Field]

  private def getField(name: String, tpe: Extractor[T]) =
    val inst = Field(name, tpe.fieldType)
    if schema(inst) then ???
    else
      schema.add(inst)
      inst

  store(
    "id",
    Extractor.S(summon[HasId[T]].identify)
  )

  def store(
      n: String,
      f: Extractor[T]
  ) =
    b += BuildAction.Store(getField(n, f), f)
    this
  end store

  def storeStr(n: String, f: T => String) =
    store(n, Extractor.S(f))
    this

  def storeBool(n: String, f: T => Boolean) =
    store(n, Extractor.B(f))
    this

  def reference[B: HasId](g: T => B) =
    val b = summon[HasId[B]]
    store(
      b.entityName + "_id",
      Extractor.S(t => b.identify(g(t)))
    )
    this

  def selfReference(tpe: String, g: T => T | Null) =
    val b = summon[HasId[T]]
    store(
      tpe + "_id",
      Extractor.S(t =>
        g(t) match
          case null => null
          case t    => b.identify(t.nn)
      )
    )
    this
  end selfReference

  def build = Indexer(actions = b.result())
end Builder
