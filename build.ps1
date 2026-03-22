$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
cd "C:\Users\bheda\Documents\ImageGalleryApp"
& ".\gradlew.bat" assembleDebug --no-daemon
