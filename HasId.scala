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

trait HasId[T]:
  def identify(v: T): String
  def entityName: String
  def relName: String =
    val vowels = "a,e,i,o,u".split(',').toSet.map(_.head)
    val ends = entityName.last
    if vowels(ends) || ends == 's' then entityName + "es"
    else if ends == 'y' then entityName.dropRight(1) + "ies"
    else entityName + "s"
end HasId

