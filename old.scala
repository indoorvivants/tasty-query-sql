
// class Classes(using ctx: Context) extends Relationship("classes"):
//   override type FieldName = "id" | "package" | "name" | "fqn"
//   override def fields = List(
//     "id" -> FieldType.Str,
//     "package" -> FieldType.Str,
//     "name" -> FieldType.Str,
//     "fqn" -> FieldType.Str
//   )

//   private lazy val defns =
//     val defs = Vector.newBuilder[Row]
//     val packages = ctx.defn.RootPackage.declarations.only[PackageSymbol]
//     packages.foreach { pkg =>
//       loggy {
//         val pkgName = pkg.name.name
//         pkg.declarations.only[ClassSymbol].foreach { cls =>
//           loggy {
//             defs += Map(
//               "id" -> classId(cls).tv,
//               "package" -> pkgName.tv,
//               "name" -> cls.name.toString.tv,
//               "fqn" -> cls.fullName.toString().tv
//             )
//           }

//         }
//       }
//     }
//     defs.result()
//   end defns

//   override def get(): Vector[Row] = defns
// end Classes

// class ClassHierarchy(using ctx: Context)
//     extends Relationship("classes_hierarchy"):
//   override type FieldName = "child_id" | "parent_id"
//   override def fields = List(
//     "child_id" -> FieldType.Str,
//     "parent_id" -> FieldType.Str
//   )

//   private lazy val defns =
//     val defs = Vector.newBuilder[Row]
//     val packages = ctx.defn.RootPackage.declarations.only[PackageSymbol]
//     packages.foreach { pkg =>
//       loggy {
//         val pkgName = pkg.name.name
//         pkg.declarations.only[ClassSymbol].foreach { cls =>
//           loggyN(cls.toDebugString) {
//             cls.parentClasses.foreach { parent =>
//               defs += Map(
//                 "child_id" -> classId(cls).tv,
//                 "parent_id" -> classId(parent).tv
//               )
//             }
//           }

//         }
//       }
//     }
//     defs.result()
//   end defns

//   override def get(): Vector[Row] = defns
// end ClassHierarchy

// trait Relationship(val name: String):
//   type FieldName <: String

//   type Row = Map[FieldName, TupleValue]

//   def fields: List[(FieldName, FieldType)]
//   def get(): Vector[Row]
//   def name(n: String)(using TypeTest[String, FieldName]) = n match
//     case s: FieldName => Option(s)
//     case _            => None
// end Relationship


