import java.sql.Connection

def populate[T](indexer: Indexer[T], values: Iterable[T])(using
    Connection
) =
  import anorm.*
  if values.nonEmpty then
    val schema = s"create table if not exists ${indexer.tableName} ${indexer.fields
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
        val query = s"insert into ${indexer.tableName}($namesList) values($valuesList)"
        SQL(query)
          .on(n*)
          .execute()
      }
    }
  end if
end populate

