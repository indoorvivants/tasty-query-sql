# Tasty-query SQL

Use [tasty-query](https://github.com/scalacenter/tasty-query), [H2](http://www.h2database.com), and some elbow grease to provide a SQL interface over Tasty definitions of a Scala 3 classpath.

At this point the relational schema is created manually, and is woefully incomplete. SQL might not even be the "right" language for this, but it for sure 
is very well known.

![Screenshot 2023-03-12 at 20 46 56](https://user-images.githubusercontent.com/1052965/224572706-87159648-3064-43b0-8d5c-9e13dc5f9805.png)

## Usage

This is a Scala CLI project.

**Launch with default classpath** (i.e. the classpath of this application itself):

```bash
$ scala-cli run . -- --web
```

This will start H2 database and open a web browser with SQL console.

**Launch with any classpath** - use the `-c` flag, for example with coursier:

```bash
$ scala-cli run . -- -c $(cs fetch -p com.indoorvivants.roach:core_native0.4_3:0.0.2) --web
```

This will start H2 database and open a web browser with SQL console.
