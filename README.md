# Battery Saver

Javaで実装したAndroid向けバッテリー最適化アプリです。
ネイビーとミントグリーンの画面に、円形の最適化ボタン、進捗リング、完了チェックを配置しています。
元のSVGは変更せず、Android用VectorDrawableに変換したものをアプリとランチャーで使用しています。

## Android Studioで開く

1. Android Studioの「Open」で、このREADMEがあるBatterySaverフォルダーを選びます。
2. Gradle Syncが終わるまで待ちます。Gradle JDKは17以上を使用してください。
3. エミュレーターまたはAndroid端末を選び、Runを押します。

- Android 8.0 / API 26以上
- Compile / Target SDK: 37
- SDK Build Tools: 36.0.0
- Android Gradle Plugin: 9.2.1 / Gradle: 9.4.1
- Java 17、外部UIライブラリ不要
- `local.properties` は現在のPCにインストールされているSDKを参照しています。

## PowerShellでビルド

```powershell
.\build.ps1
```

APKビルドとAndroid Lintを実行し、`outputs\BatterySaver.apk` を作成します。
接続した端末へインストールして起動する場合は ` .\build.ps1 -Install ` を使用できます。
APKは開発用署名です。配布用の署名はAndroid Studioの「Generate Signed App Bundle / APK」で設定してください。

ビルド用キャッシュは `.gradle-home`、開発用署名は `.android`、作業ログは `work` に保存します。
いずれもこのプロジェクト内です。Android Studioのキャッシュ先も合わせる場合は、
Settings > Build, Execution, Deployment > Build Tools > Gradle のGradle user homeを
このフォルダー内の `.gradle-home` に指定してください。

## 実際の最適化処理を追加する

**編集する場所:**

`app/src/main/java/jp/battery/saver/OptimizationEngine.java`

`performOptimization(Context context)` の `TODO` コメントの位置にコードを追加してください。
日本語で実行順序、スレッド、権限、エラー処理の注意を記載しています。

呼び出し順序は、中央ボタン → 約7.2秒の進捗アニメーション → `performOptimization()` → 完了画面です。
実処理の実行中は100%の状態で待機し、正常終了後に完了表示へ進みます。
例外が発生した場合は再試行できる状態へ戻ります。
バックグラウンドスレッドで呼び出すため、Viewの更新や権限ダイアログの表示を直接行わないでください。
追加する処理は中断と再実行を考慮して実装してください。

画面側の接続箇所は `MainActivity.java` の `runOptimizationAfterAnimation()` です。
このメソッドにも検索しやすい日本語コメントを残しています。

## 主なファイル

- `MainActivity.java`: 画面構成、バッテリー情報、進捗、画面状態の保存、ヒント画面
- `OptimizationOrbView.java`: 中央ボタン、進捗リング、光のアニメーション、完了チェック
- `OptimizationEngine.java`: 演出後に実行する処理の追加先
- `res/drawable/ic_battery_brand.xml`: 元SVGから変換したブランドアイコン

バッテリー残量・温度・状態はAndroidから取得します。
再起動後も最後に最適化が完了した日時を表示します。
画面回転、バックグラウンドからの復帰、連続タップ、画面の安全領域に対応しています。