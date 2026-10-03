# FEATO Gun-Valhalla Bridge

Gun Core / Modern Guns と ValhallaMMO FIREARMSスキルを連携するための
FEATO Minecraft Server向けBridge。

## Target Versions

- Minecraft 26.2
- Paper 26.2 build 126
- ValhallaMMO 1.10.3
- Gun Core V1.0.15
- Modern Guns V1.9.3

## Components

- Paper Plugin (`plugin/`)
- Bridge Datapack (`datapack/`, namespace `feato_gun_valhalla`)
- PoC記録 (`docs/poc.md`)

## Status

Proof of Concept。Phase 1の検証用。Phase 2/3、銃器効果、本番XPは未実装。
Phase 1の実機検証に合格するまで後続Phaseへ進まない。

## Build

Java 25でリポジトリ直下から実行:

```sh
./gradlew build
```

Paper APIは固定版。ValhallaMMOは固定配布JARを取得してSHA-512を検証し、
compile-only依存とする。第三者JARをBridgeへ同梱しない。
JSON、必須function/tag、互換性markerを静的検証する。

成果物（Plugin/Datapackは同一version）:

- `plugin/build/libs/feato-gun-valhalla-bridge-plugin-0.1.0-poc.1.jar`
- `plugin/build/distributions/feato-gun-valhalla-bridge-datapack-0.1.0-poc.1.zip`

`bridge.properties`を互換性定義の正本とする。CIはmarker不一致を拒否する。
手動Releaseでは指定versionと自動採番したrelease IDをPlugin/Datapackへ同期する。

## Manual release

[Actions → Release](https://github.com/FEATO-org/feato-gun-valhalla-bridge/actions/workflows/release.yml)
で **Run workflow** を選び、branchを`main`、`version`を例として`0.1.0-poc.2`にして実行する。
入力は`v`なしのSemVer（`1.0.0`、`1.0.0-rc.1`等）。`-poc.N`や`-rc.N`はPre-releaseになる。
現在はPhase 1 PoCのため、PoCのバージョンを使用する。
GitHub CLIからも実行可能:

```sh
gh workflow run release.yml --repo FEATO-org/feato-gun-valhalla-bridge \
  --ref main -f version=0.1.0-poc.2
```

処理:

1. default branchと入力を検証し、同名tag/Releaseがあれば停止。
2. 全versionのReleaseを直列化し、既存tagとsourceのrelease IDの最大値+1を採番。
3. `bridge.properties`、Datapackのmarker、`pack.mcmeta`を同時更新。
4. `./gradlew clean build --no-daemon`と両成果物の内包version/marker検証を実行。
5. そのmetadataを持つ専用commitへannotated tag `v<version>`を付けてpush。
6. 同一GitHub Releaseへ以下3ファイルを公開。

   - `feato-gun-valhalla-bridge-plugin-<version>.jar`
   - `feato-gun-valhalla-bridge-datapack-<version>.zip`
   - `SHA256SUMS`

metadata commitはタグだけへpushするため、`main`の開発versionは維持される。
タグのsourceをcheckoutして通常の`./gradlew build`を実行しても同じversionで再ビルドできる。
同名tag/Releaseの上書き・force pushは行わない。
タグpush後にRelease公開だけが失敗した場合、既存タグを保持してActions artifactを回収し、
同じタグの不足Releaseを管理者が作成する。再実行でタグを再作成しない。
このworkflowは配布のみで、本番サーバーへの導入やPhase 1の実機合格判定は行わない。

release toolingのローカルテスト:

```sh
python3 -m unittest discover -s scripts/tests -v
```


## Installation for verification

検証用ワールドの`datapacks/`へZIP、`plugins/`へBridgeとValhallaMMO 1.10.3を配置し、
完全停止から起動する。`/reload`、PlugMan、オンライン途中の追加は検証対象外。
Bridgeは生きたDatapack heartbeatと一致するrelease/protocol/宣言versionを確認して登録する。
markerの一致は実際に導入されたGun Core/Modern Gunsのversion検査とは別であり、Phase 2で確認予定。
不一致・heartbeat停止・必要Skill不足時はBridgeの操作を拒否しエラーを記録する。

管理者専用の`/firearms debug profile <player>`、`/firearms debug exp <player> <amount>`を
使うには`debug.enabled: true`と`feato.gunvalhalla.debug`権限が必要。
詳細な検証手順・進行条件は[PoC記録](docs/poc.md)を参照。

## Ownership / license

Bridge実装の正本は本リポジトリ。deployment/configurationの正本は
[FEATO-org/it-infrastructure-server](https://github.com/FEATO-org/it-infrastructure-server)。
PoCではインフラ側を変更しない。
FEATO-orgのMinecraft関連リポジトリで明確なライセンス方針を確認できず、LICENSEは保留。
ValhallaMMO、Gun Core、Modern Gunsのfork・配布物の直接改変は行わない。
