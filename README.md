# CellBoost

Javaで実装したAndroid向けバッテリー最適化アプリです。
ネイビーとミントグリーンの画面に、円形の最適化ボタン、進捗リング、完了チェックを配置しています。
icon/lightning-svgrepo-com.svgをAndroid用VectorDrawableに変換し、黒背景の中央に配置したアプリアイコンを使用しています。雷は従来より17%小さくし、中央を軸に右へ16度傾けています。アプリ内のブランドアイコンにも同じ調整を適用しています。

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

## ダブルクリックでビルド

エクスプローラーでプロジェクト直下の `build.bat` をダブルクリックしてください。
APKビルドとAndroid Lintを実行し、`outputs\CellBoost.apk` を作成します。
完了後は成功・失敗とAPKの保存先を表示して停止します。何かキーを押すとウィンドウを閉じます。
コマンドから停止せずに実行する場合は `build.bat --no-pause` を使用できます。

## PowerShellでビルド

```powershell
.\build.ps1
```

APKビルドとAndroid Lintを実行し、`outputs\CellBoost.apk` を作成します。
接続した端末へインストールして起動する場合は ` .\build.ps1 -Install ` を使用できます。
GradleやAndroid Studioから直接ビルドする場合も、生成されるAPK名は CellBoost.apk です（例: app/build/outputs/apk/debug/CellBoost.apk）。
APKは開発用署名です。配布用の署名はAndroid Studioの「Generate Signed App Bundle / APK」で設定してください。

ビルド用キャッシュは `.gradle-home`、開発用署名は `.android`、作業ログは `work` に保存します。
いずれもこのプロジェクト内です。Android Studioのキャッシュ先も合わせる場合は、
Settings > Build, Execution, Deployment > Build Tools > Gradle のGradle user homeを
このフォルダー内の `.gradle-home` に指定してください。

## 診断モード

バッテリー情報カードの下に「診断モード」カードを表示します。
「クイックスキャン（主な項目を短時間で確認）」と「フルスキャン（すべての項目を詳しく確認）」を
ラジオボタンで選択できます。初期値はクイックスキャンで、選択はすぐに保存し、再起動後も維持します。
実行中と結果表示のオーバーレイでは選択を変更できません。画面回転でも実行時の選択を復元します。
選択値は `OptimizationEngine.performOptimization(Context context, int master_clear, int scanMode)` へ渡します。
`SCAN_QUICK` / `SCAN_FULL` で処理を分岐できます。モード別の診断処理は未実装で、現在の演出時間は共通です。

## 設定と最適化時間

右上の歯車ボタンから設定ポップアップを開き、最適化時間とマスタークリアの値をまとめて設定できます。
「master_clear = 1 / マスタークリアの値は1です」「master_clear = 0 / マスタークリアの値は0です」の
ラジオボタンで選択し、「保存」を押すと `OptimizationScreen.master_clear_selected` に `0` または `1` を格納します。
初期値は `1` です。既に保存されている値がある場合は、その値を維持します。
選択値は `OptimizationEngine.performOptimization(Context context, int master_clear, int scanMode)` へ渡します。
後から実処理を追加するときは、このメソッド内の `master_clear` を参照してください。現時点では処理内容は変わりません。

設定ポップアップの「最適化時間」で、100%に到達するまでの時間を1〜3600秒で設定できます（初期値8秒）。
マスタークリアと秒数は「保存」で反映され、アプリの再起動後も維持されます。「キャンセル」では変更しません。
最適化を開始する直前に現在の設定をオーバーレイへ反映します。

進捗は実行ごとに乱数で生成した24区間のカーブに沿って、速度を変えながら単調に増加します。
指定時間の経過前は最大99%とし、指定時間を経過した最初の描画更新で100%になります。
画面回転では開始時刻・秒数・乱数シード・マスタークリアの選択値を復元し、同じ進捗を継続します。
Androidのアニメーション倍率設定によって指定秒数を短縮することはありません。
スキャン中はパーセントの下に、設定時間と経過時間から求めた残り時間の目安を表示します。
残り時間は分単位で端数を切り上げて表示します。1分未満も「残り約1分」と表示し、秒数は表示しません。
画面回転・復帰後も同じ開始時刻から残り時間を計算し、完了または失敗すると表示を消します。
指定時間を過ぎて実処理を待っている間は「最終処理中…」と表示します。
最適化中は「この画面を閉じないでください」を16spの明るい文字で表示し、画面の自動消灯を抑制します。
案内文に背景や枠は付けません。
縦画面ではバッテリー情報・診断モード・最適化ボタンを上から一定の間隔で並べ、高さが不足する場合はスクロールできます。
実処理を後から追加して長時間実行する場合は、従来どおり100%の状態で実処理の終了を待ちます。

## 実際の最適化処理を追加する

**編集する場所:**

`app/src/main/java/jp/battery/saver/OptimizationEngine.java`

`performOptimization(Context context, int master_clear, int scanMode)` の `TODO` コメントの位置にコードを追加してください。
日本語で実行順序、スレッド、権限、エラー処理の注意を記載しています。

呼び出し順序は、中央ボタン → 設定画面起動 → オーバーレイ内の指定秒数の進捗アニメーション → `performOptimization()` → 完了画面です。
実処理の実行中は100%の状態で待機し、正常終了後に完了表示へ進みます。
例外が発生した場合は再試行できる状態へ戻ります。
バックグラウンドスレッドで呼び出すため、Viewの更新や権限ダイアログの表示を直接行わないでください。
追加する処理は中断と再実行を考慮して実装してください。

画面側の接続箇所は `OptimizationScreen.java` の `runOptimizationAfterAnimation()` です。
このメソッドにも検索しやすい日本語コメントを残しています。

## アイコン素材

- `icon/CellBoost.svg`: 黒背景と中央の雷を含む完成版
- `icon/CellBoost.png`: 完成版の1024 x 1024 PNG
- `icon/lightning-svgrepo-com.svg`: 指定された元の雷SVG
- `icon/phone-battery-with-full-charge-and-a-bolt-symbol-svgrepo-com.svg`: 以前のアイコン素材

Androidで使用するVectorDrawableは `app/src/main/res/drawable` に配置しています。

## 主なファイル

- MainActivity.java: オーバーレイ権限取得、設定画面の起動、Serviceへの事前接続
- OptimizationOverlayService.java: オーバーレイの追加・削除と画面回転への対応
- OptimizationScreen.java: 通常画面とオーバーレイで共有する画面・バッテリー情報・最適化演出
- `OptimizationOrbView.java`: 中央ボタン、進捗リング、完了チェック
- `OptimizationProgress.java`: 指定時間で100%になるランダムな進捗カーブ
- `OptimizationEngine.java`: 演出後に実行する処理の追加先
- `res/drawable/ic_battery_brand.xml`: icon/lightning-svgrepo-com.svgから変換したブランドアイコン

バッテリー残量・温度・状態はAndroidから取得します。
再起動後も最後に最適化が完了した日時を表示します。
画面回転、バックグラウンドからの復帰、連続タップ、画面の安全領域に対応しています。

## 設定起動とオーバーレイの確認

「最適化」を押すと、Settings.ACTION_SETTINGS（android.settings.SETTINGS）を起動し、
startActivity() の直後に TYPE_APPLICATION_OVERLAY の全画面ウィンドウを追加します。
指定秒数の演出・OptimizationEngine・完了画面は、このオーバーレイ内で動きます。
オーバーレイ右上の「閉じる」で下の設定画面を表示できます。
戻る操作でアプリに戻った場合もオーバーレイを削除し、再度実行できます。

初回起動時は、権限が未許可の場合に表示の用途を説明する同意ダイアログを表示します。
「同意して設定へ」を選んだ場合だけ、Androidの「他のアプリの上に表示」の許可画面を開きます。
「あとで」または戻る操作では設定を開かず、アプリ画面に留まります。
初回の案内後は起動のたびに再表示せず、未許可のまま「最適化」を押した場合に再度同意を確認します。
権限がすでに許可されている場合は、同意ダイアログを表示しません。
初回起動時に許可した場合はアプリ画面に戻り、「最適化」を押すと設定起動とオーバーレイ表示へ進みます。
「最適化」から同意・権限許可へ進んだ場合は、許可して戻るとそのまま処理を続けます。
同意ダイアログの表示中や権限設定中に画面を回転しても、確認中の状態と最適化の開始要求を維持します。
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
