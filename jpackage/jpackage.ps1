
$jpackage = 'C:\Program Files\Java\jdk-17\bin\jpackage.exe'
$name = "MenagerieK"
$description = "MenagerieK"
$input = ".\input\"
$output = ".\output\"
$version = $args[0]
$icon = ".\menagerie.ico"
$url = "https://github.com/iguanastin/menageriek"
$modulePath = "C:\Program Files\Java\javafx-jmods-21"
$modules = "javafx.controls,javafx.swing,java.logging,java.desktop,java.rmi,java.prefs,java.sql,jdk.httpserver,java.naming,jdk.crypto.cryptoki"
$jvmoptions = "--module-path "C:\Program Files\Java\javafx-sdk-21\lib" --add-modules javafx.controls --add-opens=javafx.graphics/javafx.scene=ALL-UNNAMED --add-opens=javafx.controls/javafx.scene.control.skin=ALL-UNNAMED --add-exports javafx.base/com.sun.javafx.runtime=ALL-UNNAMED"
$jar = ".\menageriek-${version}-jar-with-dependencies.jar"

rm .\input\menageriek-*-jar-with-dependencies.jar
cp ..\target\menageriek-${version}-jar-with-dependencies.jar .\input\
cp ..\histdupe.ptx .\input\
cp ..\src\main\resources\log4j.properties .\input\
cp .\setup-schema.bat .\input\

& $jpackage --module-path "$modulePath" --add-modules $modules --input "$input" --dest "$output" -n "$name" --app-version "$version" --description "$description" --icon "$icon" --about-url "$url" --java-options "$jvmoptions" --main-jar "$jar" --verbose --win-menu --win-shortcut-prompt --win-dir-chooser --add-launcher menageriek-console=consolelauncher.properties
# & $jpackage --type app-image --module-path "$modulePath" --add-modules $modules --input "$input" --dest "$output" -n "$name" --app-version "$version" --description "$description" --icon "$icon" --java-options "$jvmoptions" --main-jar "$jar" --verbose --add-launcher menageriek-console=consolelauncher.properties
