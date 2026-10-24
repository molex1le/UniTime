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
2. Поставить тег с той же версией; текст тега — «что нового», его увидят пользователи:
   ```bash
   git tag -a v3.8 -m "- Что изменилось" && git push origin v3.8
   ```
   Строка `[важно]` в тексте делает обновление обязательным (окно без кнопки «Позже»).
3. GitHub Actions («Релиз») прогонит тесты, соберёт подписанный `UniTime-3.8.apk` и выложит его в Releases здесь и в публичном [molex1le/UniTime](https://github.com/molex1le/UniTime).
4. Установленные приложения раз в день проверяют `molex1le/UniTime` и предлагают обновиться.

Для подписи в репозитории заданы секреты `NEXTPAIR_KEYSTORE_BASE64`, `NEXTPAIR_STORE_PASSWORD`, `NEXTPAIR_KEY_PASSWORD`, `NEXTPAIR_KEY_ALIAS`, а для публичных релизов — `UNITIME_RELEASE_TOKEN` (токен с правом записи только в molex1le/UniTime).
