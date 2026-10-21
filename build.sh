#!/bin/bash
# Manual APK build (no Gradle): aapt2 -> ECJ -> d8 -> zipalign -> apksigner
set -e
export PATH=~/workspace/build-tools/jdk-17.0.20.1+1-jre/bin:~/android-sdk/build-tools/34.0.0:$PATH
PROJ=~/workspace/india-tv
B=$PROJ/build
ANDROID_JAR=~/android-sdk/platforms/android-34/android.jar
JBIN=~/workspace/build-tools/jdk-17.0.20.1+1-jre/bin/java
# Launcher images are generated artifacts (not committed); regenerate if missing.
if [ ! -f $PROJ/app/src/main/res/drawable/banner.png ]; then
  python3 $PROJ/assets-img/make_img.py
fi
rm -rf $B && mkdir -p $B/compiled $B/classes $B/dex $B/gen
aapt2 compile --dir $PROJ/app/src/main/res -o $B/compiled/
sed 's|<manifest xmlns|<manifest package="com.hari.indiatv" xmlns|' $PROJ/app/src/main/AndroidManifest.xml > $B/AndroidManifest.xml
aapt2 link -o $B/base.apk -I $ANDROID_JAR --manifest $B/AndroidManifest.xml \
  --min-sdk-version 21 --target-sdk-version 34 --version-code 6 --version-name "1.4" \
  --java $B/gen $B/compiled/*.flat
$JBIN -jar ~/workspace/build-tools/ecj.jar -source 1.8 -target 1.8 -nowarn -cp "$ANDROID_JAR" -d $B/classes \
  $PROJ/app/src/main/java/com/hari/indiatv/*.java $B/gen/com/hari/indiatv/R.java
d8 --min-api 21 --lib $ANDROID_JAR --output $B/dex $(find $B/classes -name "*.class" | tr '\n' ' ')
(cd $B/dex && aapt add ../base.apk classes.dex >/dev/null)
zipalign -f -p 4 $B/base.apk $B/india-tv-aligned.apk
apksigner sign --ks ~/workspace/instacount/build-tmp/debug.keystore --ks-pass pass:android --key-pass pass:android \
  --out $B/IndiaTV-v1.4.apk $B/india-tv-aligned.apk
apksigner verify $B/IndiaTV-v1.4.apk && echo "OK: $B/IndiaTV-v1.4.apk"
