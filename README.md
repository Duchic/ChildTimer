# Časovač pro děti

Nativní Android aplikace v Kotlinu a Jetpack Compose podle odsouhlaseného návrhu.

## První verze

- Vlastní odpočet od 1 do 180 minut, ovládání +/− i přímé zadání.
- Čtyři rychlé volby (výchozí 5, 10, 15 a 30 minut), spuštění jedním klepnutím.
- Barevný kruh ubývá po směru hodinových ručiček. Celý kruh odpovídá původní délce odpočtu.
- Pět barev: tyrkysová, modrá, fialová, oranžová a růžová.
- Pauza, pokračování a ukončení s potvrzením.
- Zámek ovládání, odemčení podržením po dobu 3 sekund; alternativa pro čtečku obrazovky.
- Uložení nastavení i aktivního odpočtu, obnovení po otočení telefonu nebo novém otevření aplikace.
- Krátký zvuk po skončení a systémová notifikace. Přesný systémový budík funguje i na pozadí.
- Volitelné ponechání rozsvíceného displeje během odpočtu.
- České rozhraní, bez účtu, reklam a síťového oprávnění.

## Sestavení

JDK 17 nebo 21, Android SDK Platform 37.0 a Build Tools 36.0.0. Gradle wrapper je součástí projektu.
V Android Studiu otevřete kořenovou složku projektu. Nastavte SDK v `local.properties` (soubor je lokální a nepatří do Gitu):

```properties
sdk.dir=C\:/cesta/k/Android/Sdk
```

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

APK vzniká v `app/build/outputs/apk/debug/app-debug.apk`. Minimální Android je 8.0 (API 26).
Jde o ladicí sestavení pro vyzkoušení; vydání do Google Play vyžaduje vlastní podpis.

V tomto počítači je SDK v `.tools/android-sdk`. Kvůli nefunkční síťové proxy Gradlu byla sestavena lokální kopie dostupných Maven knihoven. Pro místní ověření lze použít:

```powershell
.\gradlew.bat --offline -I .tools/offline.init.gradle :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Složka `.tools` nepatří do zdrojového kódu a na jiném počítači není potřeba, pokud má Gradle dostupné repozitáře Google a Maven Central.

## Ověření na telefonu

1. Nastavte 1 minutu, spusťte odpočet a ověřte prázdný kruh i krátké upozornění na konci.
2. Pozastavte, chvíli počkejte a pokračujte. Čas během pauzy neubývá.
3. Otočte telefon, přepněte aplikaci na pozadí a zhasněte displej. Zkontrolujte správný čas po návratu i upozornění na pozadí.
4. Nastavte čtyři jiné rychlé volby a barvu, aplikaci znovu otevřete a ověřte uložení.
5. Vyzkoušejte zámek: krátké klepnutí ho neodemkne, třísekundové podržení ano.
6. Vyzkoušejte odmítnutí notifikací, vypnutý zvuk, velké systémové písmo a režim na šířku.

Na Androidu 12/12L aplikace před spuštěním požádá o povolení přesných budíků; na Androidu 13+ požádá o notifikace. Zvuk respektuje systémovou hlasitost budíku. Zámek brání náhodným dotykům, není rodičovskou kontrolou telefonu.

Odpočet nepokračuje přes restart zařízení; po otevření se zobrazí jako ukončený. Vynucené zastavení aplikace v systémovém nastavení ruší systémové budíky. Tyto situace nelze zaměňovat s běžným přepnutím do jiné aplikace.

## Struktura

- `MainActivity.kt`: Compose obrazovky a ovládání.
- `Countdown.kt`: čistá, testovatelná logika odpočtu s monotónním časem.
- `TimerStore.kt`: lokální uložení nastavení a stavu.
- `TimerAlarm.kt`: systémový budík, přijímač, notifikace a krátký zvuk.
- `CountdownTest.kt`: testy běhu na pozadí, pauzy, pokračování, dokončení a hranic vstupu.

Konfigurace SDK vychází z [dokumentace Android build](https://developer.android.com/build); plánování upozornění z [dokumentace systémových budíků](https://developer.android.com/develop/background-work/services/alarms).
