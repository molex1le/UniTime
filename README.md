# UniTime

UniTime — Android-приложение с расписанием БГУИР: следующая пара в уведомлении и на экране блокировки, виджет, лента до сессии, дедлайны, общие окна с друзьями.

Данные — из открытого API `iis.bsuir.by`. Неофициальное приложение, не связано с БГУИР.

## Сборка

```bash
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/UniTime-<версия>.apk`. Нужны JDK 17+ и Android SDK (путь в `local.properties`, `sdk.dir=...`).

## Выпуск новой версии

1. Поднять `versionCode` и `versionName` в `app/build.gradle.kts`, закоммитить в `main`.
2. Поставить тег с той же версией и отправить его:
   ```bash
   git tag v3.5 && git push origin v3.5
   ```
3. GitHub Actions («Релиз») прогонит тесты, соберёт подписанный `UniTime-3.5.apk` и выложит его в Releases.

Для подписи в репозитории заданы секреты `NEXTPAIR_KEYSTORE_BASE64`, `NEXTPAIR_STORE_PASSWORD`, `NEXTPAIR_KEY_PASSWORD`, `NEXTPAIR_KEY_ALIAS`.
