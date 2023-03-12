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
