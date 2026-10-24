import {
  mkdtempSync,
  readFileSync,
  writeFileSync,
  rmSync,
  existsSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { spawnSync } from "node:child_process";
import { randomBytes } from "node:crypto";

// Application migration verification against a configured development PostgreSQL server.
// Does not start, build or alter Docker containers or deployment configuration.
const root = resolve(import.meta.dirname, "../..");
const envPath = process.env.LMS_ENV_FILE || join(root, "backend/.env");
const values = existsSync(envPath)
  ? Object.fromEntries(
      readFileSync(envPath, "utf8")
        .split(/\r?\n/)
        .filter(
          (line) =>
            line.trim() && !line.trim().startsWith("#") && line.includes("="),
        )
        .map((line) => [
          line.slice(0, line.indexOf("=")).trim(),
          line.slice(line.indexOf("=") + 1).trim(),
        ]),
    )
  : {};
const env = { ...values, ...process.env };
if (!env.DB_PASSWORD)
  throw new Error(
    "Provide development DB_PASSWORD in the environment or LMS_ENV_FILE.",
  );
const java = env.JAVA_HOME ? join(env.JAVA_HOME, "bin/java") : "java";
const jar = env.JAVA_HOME ? join(env.JAVA_HOME, "bin/jar") : "jar";
const artifact = join(
  root,
  "backend/user-service/target/user-service-1.0.0.jar",
);
if (!existsSync(artifact))
  throw new Error(
    "Build the backend JARs with Java 25 before verifying schemas.",
  );
const temp = mkdtempSync(join(tmpdir(), "lms-schema-check-"));
try {
  const extracted = spawnSync(jar, ["xf", artifact, "BOOT-INF/lib"], {
    cwd: temp,
    stdio: "pipe",
  });
  if (extracted.status !== 0)
    throw new Error(
      "The application dependencies could not be extracted with the JDK jar tool.",
    );
  const source = join(temp, "SchemaVerification.java");
  writeFileSync(
    source,
    `
import java.sql.*;
import java.nio.file.*;
import org.flywaydb.core.Flyway;
public class SchemaVerification {
 public static void main(String[] args) throws Exception {
  String user=System.getenv().getOrDefault("DB_USERNAME","lms_user"), password=System.getenv("DB_PASSWORD");
  String base="jdbc:postgresql://"+System.getenv().getOrDefault("DB_HOST","127.0.0.1")+":"+System.getenv().getOrDefault("DB_PORT","5432");
  String prefix=args[1];
  if(!prefix.matches("lms_app_check_[a-f0-9]{12}")) throw new IllegalArgumentException("Unsafe test database prefix");
  try(Connection admin=DriverManager.getConnection(base+"/postgres",user,password)) {
   for(String module:new String[]{"user-service","course-service","enrollment-service","content-service","notification-service","payment-service"}) {
    String database=prefix+"_"+module.replace("-service","");
    try(Statement statement=admin.createStatement()) {statement.execute("CREATE DATABASE \\\""+database+"\\\"");}
    try {
     var flyway=Flyway.configure().dataSource(base+"/"+database,user,password)
      .locations("filesystem:"+Path.of(args[0],"backend",module,"src/main/resources/db/migration"))
      .cleanDisabled(true).load();
     flyway.migrate();flyway.validate();
     if(flyway.info().pending().length!=0) throw new IllegalStateException("Pending migrations");
     if(module.equals("course-service")) {
      try(Connection connection=DriverManager.getConnection(base+"/"+database,user,password);Statement statement=connection.createStatement()) {
       try(ResultSet result=statement.executeQuery("select count(*) from categories where name in ('Software Development','Data Science','Design','Business','Personal Development')")) {
        result.next();if(result.getInt(1)!=5) throw new IllegalStateException("Default categories must be English");
       }
       try(ResultSet result=statement.executeQuery("select column_default from information_schema.columns where table_name='courses' and column_name='language'")) {
        if(!result.next()||!result.getString(1).contains("English")) throw new IllegalStateException("Default course language must be English");
       }
      }
     }
     System.out.println("VERIFIED "+module);
    } finally { try(Statement statement=admin.createStatement()) {statement.execute("DROP DATABASE \\\""+database+"\\\" WITH (FORCE)");} }
   }
  }
 }
}
`,
  );
  const result = spawnSync(
    java,
    [
      "--class-path",
      join(temp, "BOOT-INF/lib/*"),
      source,
      root,
      `lms_app_check_${randomBytes(6).toString("hex")}`,
    ],
    { env, encoding: "utf8", timeout: 120_000, maxBuffer: 4 * 1024 * 1024 },
  );
  if (result.status !== 0) {
    process.stderr.write(
      (result.stderr || result.stdout || "Migration verification failed").slice(
        -6000,
      ),
    );
    process.exitCode = 1;
  } else {
    const passed = result.stdout
      .split("\n")
      .filter((line) => line.startsWith("VERIFIED "));
    for (const line of passed) console.log(line);
    if (passed.length !== 6)
      throw new Error("Not all application schemas were verified.");
    console.log(
      "All six schemas migrated and validated in fresh, disposable databases.",
    );
  }
} finally {
  rmSync(temp, { recursive: true, force: true });
}
