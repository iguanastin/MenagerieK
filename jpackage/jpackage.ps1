
$jpackage = 'C:\Program Files\Java\jdk-17\bin\jpackage.exe'
$name = "MenagerieK"
$copyright = [char]0x00A9 + " Austin Thompson 2023"
$description = "MenagerieK"
$input = ".\input\"
$output = ".\output\"
$version = $args[0]
$icon = ".\menagerie.ico"
$url = "https://github.com/iguanastin/menageriek"
$jvmoptions = "--module-path 'C:\Program Files\Java\javafx-sdk-21\lib' --add-modules javafx.controls --add-opens=javafx.graphics/javafx.scene=ALL-UNNAMED"
$jar = ".\menageriek-${version}-jar-with-dependencies.jar"

rm .\input\menageriek-*-jar-with-dependencies.jar
cp ..\target\menageriek-${version}-jar-with-dependencies.jar .\input\
cp ..\histdupe.ptx .\input\
cp ..\src\main\resources\log4j.properties .\input\
cp .\setup-schema.bat .\input\

& $jpackage --input "$input" --dest "$output" -n "$name" --app-version "$version" --copyright "$copyright" --description "$description" --icon "$icon" --about-url "$url" --win-menu --win-shortcut-prompt --win-dir-chooser --java-options "$jvmoptions" --main-jar "$jar" --verbose --add-launcher menageriek-console=consolelauncher.properties
