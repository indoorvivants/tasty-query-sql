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

enum Extractor[T](val fieldType: FieldType):
  case S[T](f: T => String | Null) extends Extractor[T](FieldType.S)
  case B[T](f: T => Boolean) extends Extractor[T](FieldType.B)
  case I[T](f: T => Int) extends Extractor[T](FieldType.I)

  def extract(v: T) = this match
    case S(f) =>
      Option(f(v)).map(TupleValue.S.apply)
    case B(f) =>
      Option(f(v)).map(TupleValue.B.apply)
    case I(f) =>
      Option(f(v)).map(TupleValue.I.apply)
end Extractor
