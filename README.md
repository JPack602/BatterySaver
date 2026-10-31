# CellBoost

Javaで実装したAndroid向けバッテリー最適化アプリです。
ネイビーとミントグリーンの画面に、円形の最適化ボタン、進捗リング、完了チェックを配置しています。
lightning-svgrepo-com.svgをAndroid用VectorDrawableに変換し、黒背景の中央に配置したアプリアイコンを使用しています。アプリ内のブランドアイコンにも同じSVGを使用しています。

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

呼び出し順序は、中央ボタン → 設定画面起動 → オーバーレイ内の約7.2秒の進捗アニメーション → `performOptimization()` → 完了画面です。
実処理の実行中は100%の状態で待機し、正常終了後に完了表示へ進みます。
例外が発生した場合は再試行できる状態へ戻ります。
バックグラウンドスレッドで呼び出すため、Viewの更新や権限ダイアログの表示を直接行わないでください。
追加する処理は中断と再実行を考慮して実装してください。

画面側の接続箇所は `OptimizationScreen.java` の `runOptimizationAfterAnimation()` です。
このメソッドにも検索しやすい日本語コメントを残しています。

## 主なファイル

- MainActivity.java: オーバーレイ権限取得、設定画面の起動、Serviceへの事前接続
- OptimizationOverlayService.java: オーバーレイの追加・削除と画面回転への対応
- OptimizationScreen.java: 通常画面とオーバーレイで共有する画面・バッテリー情報・最適化演出
- `OptimizationOrbView.java`: 中央ボタン、進捗リング、光のアニメーション、完了チェック
- `OptimizationEngine.java`: 演出後に実行する処理の追加先
- `res/drawable/ic_battery_brand.xml`: lightning-svgrepo-com.svgから変換したブランドアイコン

バッテリー残量・温度・状態はAndroidから取得します。
再起動後も最後に最適化が完了した日時を表示します。
画面回転、バックグラウンドからの復帰、連続タップ、画面の安全領域に対応しています。

## 設定起動とオーバーレイの確認

「最適化する」を押すと、Settings.ACTION_SETTINGS（android.settings.SETTINGS）を起動し、
startActivity() の直後に TYPE_APPLICATION_OVERLAY の全画面ウィンドウを追加します。
既存の約7.2秒の演出・OptimizationEngine・完了画面は、このオーバーレイ内で動きます。
オーバーレイ右上の「閉じる」で下の設定画面を表示できます。
戻る操作でアプリに戻った場合もオーバーレイを削除し、再度実行できます。

初回は「他のアプリの上に表示」の許可画面を開きます。
CellBoost を許可して戻ると、設定起動とオーバーレイ表示へ進みます。
Android 11以降は許可対象アプリの一覧が表示される場合があります。
拒否した場合は設定起動・オーバーレイを実行せず、ボタンから再試行できます。

端末によっては、通常の表示権限に加えて、開発者向けオプションの
「設定画面へのオーバーレイを許可（Allow screen overlays on Settings）」を有効にする必要があります。
これは端末側で設定してください。OSやメーカーが表示を抑制する画面への表示は保証できません。
Androidの標準仕様と設定画面側の制限:
https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_APPLICATION_OVERLAY
https://android.googlesource.com/platform/packages/apps/Settings/+/b05ab4233181a178167f637de31e980231071b8c%5E1..b05ab4233181a178167f637de31e980231071b8c/

### 測定処理を追加する位置

- MainActivity.launchWhenReady(): startActivity(new Intent(Settings.ACTION_SETTINGS)) の直前・直後
- OptimizationOverlayService.showPreparedOverlay(): WindowManager.addView() の直前・直後
- OptimizationScreen.getView(): オーバーレイのルートView。初回描画の観測用コールバックを追加する場合の対象

測定処理は追加していません。SystemClockなどの既存処理は演出の進捗管理用です。
起動から追加要求までに意図的な遅延、Handler.post、Service起動待ちは挟みません。
Serviceはアプリ画面の作成時にbindし、表示するViewと初期状態も設定起動より前に準備します。
startActivity() が戻る時点は、設定ActivityのonCreate完了や初回描画の完了を保証しません。
addView() が戻る時点も初回表示完了ではありません。実際の可視化はOSの描画タイミングに依存します。

ServiceはMainActivityとのbindで保持します。Activityの停止中も動作し、
オーバーレイを閉じたとき・アプリへ戻ったとき・Service終了時にはView、Receiver、実行中の処理を解放します。
MainActivity自体が破棄された場合はbindも解放され、オーバーレイを終了します。
バックグラウンドで無期限に常駐する仕組みにはしていません。