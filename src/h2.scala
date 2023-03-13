import java.sql.Connection

def populate[T](indexer: Indexer[T], values: Iterable[T])(using
    Connection
) =
  import anorm.*
  if values.nonEmpty then
    def pkey(f: Field) = if f.primaryKey then " PRIMARY KEY" else ""
    def fkey(f: Field) = f.foreignKey match
      case None                         => ""
      case Some(ForeignKey(rel, field)) => s" REFERENCES $rel($field)"

    val schema =
      s"create table if not exists ${indexer.tableName} ${indexer.fields
          .map {

            case f if f.tpe == FieldType.S =>
              s"${f.name} VARCHAR${pkey(f)}${fkey(f)}"
            case f if f.tpe == FieldType.B =>
              s"${f.name} BOOLEAN${pkey(f)}${fkey(f)}"
            case f if f.tpe == FieldType.I =>
              s"${f.name} INTEGER${pkey(f)}${fkey(f)}"
          }
          .mkString("(", ", ", ")")}"

    SQL(schema).execute()
    SQL("create table if not exists errors(definition varchar, error varchar)").execute()

    values.foreach { v =>
      try
        val row = indexer.go(v)
        val nonNull = row.filter(_._2.nonEmpty).map(_._1).toList
        val namesList = nonNull.map(_.name).mkString(", ")
        val valuesList = nonNull.map("{" + _.name + "}").mkString(", ")
        val n = nonNull.map(n =>
          NamedParameter(n.name, row(n).map(_.asParameterValue).orNull)
        )
        val query =
          s"insert into ${indexer.tableName}($namesList) values($valuesList)"
        SQL(query)
          .on(n*)
          .execute()
      catch
        case exc =>
          SQL("insert into errors values({definition}, {error})")
            .on("definition" -> v.toString, "error" -> exc.toString)
            .execute()
    }
  end if
end populate
