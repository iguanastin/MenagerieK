
$jpackage = 'C:\Program Files\Java\jdk-17\bin\jpackage.exe'
$name = "MenagerieK"
$copyright = [char]0x00A9 + " Austin Thompson 2023"
$description = "MenagerieK"
$input = ".\input\"
$output = ".\output\"
$version = $args[0]
$icon = ".\menagerie.ico"
$url = "https://github.com/iguanastin/menageriek"
$jvmoptions = "--add-opens=javafx.graphics/javafx.scene=ALL-UNNAMED"
$jar = ".\menageriek-${version}-jar-with-dependencies.jar"

rm .\input\menageriek-*-jar-with-dependencies.jar
cp ..\target\menageriek-${version}-jar-with-dependencies.jar .\input\
cp ..\histdupe.ptx .\input\
cp ..\src\main\resources\log4j.properties .\input\
cp .\setup-schema.bat .\input\

& $jpackage --module-path 'C:\Program Files\Java\javafx-jmods-19.0.2.1' --add-modules javafx.controls,javafx.swing,java.logging,java.desktop,java.rmi,java.prefs,java.sql,jdk.httpserver,java.naming,jdk.crypto.cryptoki --input "$input" --dest "$output" -n "$name" --app-version "$version" --copyright "$copyright" --description "$description" --icon "$icon" --about-url "$url" --java-options "$jvmoptions" --main-jar "$jar" --verbose --win-menu --win-shortcut-prompt --win-dir-chooser --add-launcher menageriek-console=consolelauncher.properties
# & $jpackage --type app-image --module-path 'C:\Program Files\Java\javafx-jmods-19.0.2.1' --add-modules javafx.controls,javafx.swing,java.logging,java.desktop,java.rmi,java.prefs,java.sql,jdk.httpserver,java.naming,jdk.crypto.cryptoki --input "$input" --dest "$output" -n "$name" --app-version "$version" --copyright "$copyright" --description "$description" --icon "$icon" --java-options "$jvmoptions" --main-jar "$jar" --verbose --add-launcher menageriek-console=consolelauncher.properties
