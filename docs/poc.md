# FIREARMS Bridge PoC

検証日: 2026-10-03 (Asia/Tokyo)。実装対象は固定版のみ。

## Gate / status

- Phase 1: 部分成功。専用Java実装、固定配布JARに対するコンパイル、純粋ロジック・設定のテストまで。
- Phase 2: 未実施。Phase 1の実機合格待ち。
- Phase 3: 未実施。Phase 2の実機合格待ち。
- Tactical Reload / Weapon Bash / Deadeye Night Vision / Demolitionist / Bulwark: 未実装。設定キーのみ予約。

ユーザー確認で利用可能なのは本番環境のみ。検証用サーバー・実プレイヤーセッションがなく、
本番への配置・再起動・設定変更を行っていない。Paperサーバーは今回起動していない。
実機成功を推測で補わず、この地点で後続Phaseを停止する。

## Repository decisions

GitHub CLIで指定リポジトリ不存在を確認後、FEATO-org/feato-gun-valhalla-bridgeを作成。
Minecraft関連のit-infrastructure-server、feato_horsemanship、feato-coin-exchange、
feato_ancient_coinはいずれもPUBLIC / mainであったため、それに合わせる。
Minecraft関連リポジトリのlicenseInfoはすべてnull。LICENSEを保留。
他用途のsupport-feato-systemのMIT / developをMinecraft運用へ推測適用しない。
PluginとDatapackは本リポジトリで管理。インフラリポジトリは読み取りのみ。

## Target evidence

| 対象 | 証拠 | 範囲 |
|---|---|---|
| Paper 26.2 build 126 | 固定API `io.papermc.paper:paper-api:26.2.build.126-stable`、[build API](https://fill.papermc.io/v3/projects/paper/versions/26.2/builds/126) | コンパイル、配布metadata・JARハッシュ確認。起動未実施 |
| Minecraft 26.2 | Paper JAR内`version.json` | Java 25、data pack 107.1、protocol 776。最新への読み替えなし |
| ValhallaMMO 1.10.3 | [固定配布JAR](https://cdn.modrinth.com/data/rxrgsoud/versions/GkeSDJSq/ValhallaMMO_1.10.3.jar)、plugin.yml、javap | 公開APIと`shouldPersist(Profile)`のbytecode確認 |
| ValhallaMMOソース | [既存checkoutと同じcommit](https://github.com/Athlaeos/ValhallaMMO/tree/231f9758d51a14bff2214d7c67428960b15648d4)、pom revision 1.10.3 | Registry、保存、Perk、respec経路の根拠。Bridgeへコピーしない |
| Gun Core V1.0.15 / Modern Guns V1.9.3 | ユーザー指定の固定対象を互換性定義へ記録 | 対象Datapack実体の検証はPhase 2。resource packをDatapackと扱わない |

ValhallaMMO配布JARのSHA-512:

```text
e04a1e8f39e009e141fe8f07dd1eea85ad06c5850e63e5518618c316e3b4822179ab5bab571f1a5fc6ccf35c17de4baaae07c86e7fd9b374b6eb1f6cedd528a6
```

Paper配布JARのSHA-256:

```text
9c95eb088b903d9ed96cd457838f3078f02407a965dc8ae36d2c4e551a04355a
```

## Phase 1 implementation

- `FirearmsSkill extends Skill`、`FirearmsProfile extends Profile`。相互の型を明示する。
- `FirearmsRegistrationService`だけがRegistryを変更。`ValhallaIntegration`が外部API境界。
- 起動時にMinecraft / Paper build / Valhalla plugin versionを検査。
- MINING / LIGHT_WEAPONS / LIGHT_ARMOR / HEAVY_ARMOR / ARCHERY不足、既存FIREARMS、
  同名テーブルを使う登録済みProfile、オンライン途中の登録を拒否。上書き・後付けProfile合成なし。
- Profile登録→テーブルとカラム確認→Skill登録の順。APIのSQLエラー握りつぶしを成功と判定しない。
- Lv0の少量XPが保存条件から外れるValhalla実装を確認。公開APIの`setShouldForcePersist(true)`を
  FIREARMS templateへ設定し、blank profileへコピー。Valhallaのdirty/save経路を使用する。
- XPは`Skill#addEXP(..., COMMAND)`のみ。本番XP式・銃イベント・Bridge独自DBなし。
- Recruitと三択Abilityは取得・排他・respec確認用のPoC Perk。最終スキルツリー/ゲーム効果なし。
  三択の各Perkが`perks_locked_add`で他2つを指定。選択と再計算はValhallaに委ねる。
- 管理者debugはpermissionと`debug.enabled`の両方を要求。amountは有限・正数・100万以下。
  player名はオンラインの完全一致で解決し、入力をコマンドへ連結しない。
- Profile出力はpersistent XP / level / unlocked Perkと、persistentおよびeffective lockを区別。
  XP付与直後の出力は即時値。非同期のPerk再計算やディスクへの保存完了を意味しない。

### Plugin / Datapack handshake

`bridge.properties`がPlugin version / release ID / protocol / 各対象versionの正本。
Datapackは`fgv_bridge` objectiveの専用fake playerにrelease / protocol / 対象versionと
heartbeatを記録。load/tick tagからBridge専用functionを実行する。
Pluginは20tickごとに読み、一致するmarkerが動いたことを確認してから登録する。
起動中は短時間のログインを拒否する。100tick marker不在、60tick heartbeat停止、
identity不一致、登録後のmarker消失を明示的なエラーとする。
管理者操作直前にもidentityを検査するが、定期heartbeat計測のサンプル数は増やさない。

これは互換性確認専用であり、shot通知方式ではない。
このmarkerはGun Core/Modern Gunsの実導入versionを証明しない。
Phase 2で同期通知または順序・一意性を保証したqueue等を実証するまではshot連携なし。

登録が途中失敗した場合に公開unregister APIは使わない。自動再登録やprivate APIによるrollbackを
行わず、Bridgeをfail-closedにし、完全再起動を要求する。登録済みProfileやデータは削除しない。
登録後に失敗したSkillは非levelable / 非navigableとなるが、既に開いているGUIの完全無効化や
外部Pluginが直接Perkを操作する経路は検証していない。完全停止からの再起動が必要。

### Verification matrix

| 確認項目 | 今回 | 合格に必要な実機証拠 |
|---|---|---|
| Skill / Profile登録 | 固定JARへのコンパイル、登録前提guardの単体テストのみ | 起動ログ、銃器メニュー、SQL schema |
| XP / Level Up | 入力制約、公開addEXP signature確認のみ | Lv0少量XPとLv1以上、実event・level値 |
| Profile save/load | 保存条件bytecode確認のみ | Valhalla DBのFIREARMS行と読み戻し一致 |
| Logout/Login | 未確認 | 同一UUIDでXP/level/Perk維持 |
| Server restart | 未確認 | 正常停止・完全再起動後の値とDB整合 |
| Perk取得 / 三択 | 設定構造・相互lockの静的テストのみ | 各選択の他2つ拒否、再接続後も排他 |
| respec / recalculation | 公開経路とソース確認のみ | Valhalla標準respecで再選択・XP/level期待値 |
| 既存Skillへの副作用 | 必須Skill guardテストのみ | 他SkillのXP/level/Perkを前後比較 |

### Reproducible live test procedure (dedicated test server)

1. Paper 26.2 build 126 / Java 25 / ValhallaMMO 1.10.3を検証用ワールドへ用意し、
   必須5スキルを有効にする。DB方式とValhalla configを記録。既存本番DBを使わない。
2. 同一buildのBridge JAR/ZIPを配置し、`debug.enabled: true`に設定して完全起動。
   起動ログにmarker一致・登録完了があること、`profiles_firearms`の必要カラムを確認。
3. 権限のないプレイヤーではdebug実行不可を確認。権限は管理者/consoleだけに与える。
4. テストプレイヤーで`/firearms debug profile <player>`を記録し、既存5スキルとPOWERの
   XP/level/Perk/pointを標準ValhallaコマンドまたはDBでbaseline記録。
5. `/firearms debug exp <player> 1`でLv0少量XPを作り、正常logout後にDBとlogin時の値を確認。
   少量XPの保存はLv1以上の検証と分ける。
6. `/firearms debug exp <player> 100`等でlevel-upを確認し、必要なら20まで試験用XPを付与。
   XP curveは検証専用`100 * level`。grant requested量とValhallaが実際に受け入れた量を区別。
7. `/skills`で銃器のRecruitとAbilityを取得。Valhalla側pointが必要。
   各三択を個別に試し、残り2つは取得できないことをGUIとeffective lockで確認。
   非同期再計算後に再度profileを確認し、再接続・再起動後も選択維持を確認。
8. テスト用プレイヤーのみ、Valhalla標準`/valhalla reset SKILLS_REFUND_EXP <player>`でrespec。
   標準仕様で他SkillのPerkもresetされるため、これは専用試験環境のみで実施。
   FIREARMS XP/level保持、三択lock解除、別Ability再取得と再計算を確認。
   単一skill resetも必要なら`/valhalla reset skill FIREARMS <player>`を使い別途結果を記録。
9. 正常logout、`stop`、完全再起動、同一UUID loginで値を再確認。
   既存Skillの変化を標準level-up/respecの期待動作と意図しない変化に分けて記録。
10. 新規検証ワールドでDatapack不在・marker不一致・必須Skillを1つずつ無効化し、
    FIREARMS登録停止のログを確認。既存データのある本番で異常系を実行しない。

いずれかで永続化不能、登録不安定、データ破損、respec/再計算不能、公開API経路不成立が
判明したらPhase 1で停止し、再現条件・ログ・DB状態を残す。回避実装を推測で追加しない。

## Phase 2 (not implemented)

logical shot / burst / shotgun multi-target / slowcast marker / explosion帰属 / miss /
multiplayer / shooter一致 / stable weapon IDすべて未検証。
`gbg.id`をshot IDと扱わず、`EntityShootBowEvent = 1 shot`と仮定しない。
既知の技術調査は維持。必要な固定Datapack本体を準備した上で、27銃を検証する。
shared storage最新1件poll、後tickのgbg:gun_data/macro、nearest entity対応付けは採用しない。

## Phase 3 (not implemented)

`custom_damage_system=true`環境でのValhalla damage / Crit / HP・absorption / iframe /
XP feasibilityはすべて未検証。
getFinalDamageを実損失と扱う、MONITOR書換え、追加/damage、二重Crit抽選は実装しない。

## Build verification

Java 25 / Gradle 9.2.1でリポジトリ直下から実行:

```sh
./gradlew build --no-daemon
./gradlew clean build --no-daemon
```

両方BUILD SUCCESSFUL。最終clean buildで固定Valhalla JAR取得・SHA-512検証から実行。
JUnit: 27件、failures=0、errors=0、skipped=0。
対象は設定parse、登録前提（5必須Skill・重複・late registration）、heartbeat状態遷移、
互換性metadata、XP入力の異常系。Valhalla runtimeやプレイヤーをmockして成功判定していない。
Datapack JSON/必須function/tag/marker一致の検査成功。
JARにValhallaクラスを同梱しないこと、ZIP直下のpack.mcmetaを確認。

成果物:

- `plugin/build/libs/feato-gun-valhalla-bridge-plugin-0.1.0-poc.1.jar`
- `plugin/build/distributions/feato-gun-valhalla-bridge-datapack-0.1.0-poc.1.zip`

Paper APIのPlayerLoginEvent利用にdeprecation noteが出るが、26.2 build 126でコンパイル成功。
Gradle 9.2.1にもdeprecation警告があり、Gradle 10への更新は対象外。
これらの成功は実機検証の代わりにならない。

## Known limitations / next gate

- 検証用サーバー・実クライアントが必要。現時点で全Phaseの成立証明は完了していない。
- SQL schema検査・初期化順序・Datapack heartbeatの実機動作は未確認。
- SQLite/MySQL/Redis保存方式の実機試験は未実施。MySQLの場合はcolumn metadataも確認する。
- `/reload`、PlugMan、オンライン途中の追加はサポート対象外。公開Registry変更を検知したら停止。
- Gun Core/Modern Gunsの対象Datapack本体と実行経路の確認はPhase 2へ保留。
- LICENSEは方針確定待ち。release workflow・本番導入・インフラ変更は未実施。
