# FIREARMS Bridge PoC

初期検証日: 2026-10-03、進捗更新: 2026-10-06 (Asia/Tokyo)。実装対象は固定版のみ。

## Gate / status

- Phase 1: **Complete**。ユーザー指示により、実機確認を含め完了として扱う。以下の検証記録の範囲は維持する。
- Phase 2: **Blocked / stop condition reached**。固定版native slowcastの一意な帰属契約が成立しないため停止。Ready for live verificationではない。
- Phase 3: **Not started**。Phase 2の実機合格待ち。
- Tactical Reload / Weapon Bash / Deadeye Night Vision / Demolitionist / Bulwark: 未実装。設定キーのみ予約。

2026-10-04、ユーザーから起動失敗を修正し正常動作したとの報告を受けた。
以下の「ユーザー実機確認済み」はユーザー報告の範囲であり、今回Codexが本番を操作した結果ではない。
Lv0時の特定XP値の保存一致、Level Up、完全再起動後の永続化、DB上の値、Perk三択排他、
respec/recalculationは未確認。respecは実装進行を優先して後回しとし、専用の回避処理は追加しない。
2026-10-06のユーザー指示によりPhase 1をCompleteとする。respec/recalculationはDeferred / non-blocking。
Phase 2は固定版を調査して停止条件を確認した。Phase 3とゲーム効果は未実装。

## Repository decisions

GitHub CLIで指定リポジトリ不存在を確認後、FEATO-org/feato-gun-valhalla-bridgeを作成。
Minecraft関連のit-infrastructure-server、feato_horsemanship、feato-coin-exchange、
feato_ancient_coinはいずれもPUBLIC / mainであったため、それに合わせる。
Minecraft関連リポジトリのlicenseInfoはすべてnull。LICENSEを保留。
他用途のsupport-feato-systemのMIT / developをMinecraft運用へ推測適用しない。
PluginとDatapackは本リポジトリで管理。初期PoC時のインフラ参照は読み取りのみだったが、
その後の依頼で[インフラPR #24](https://github.com/FEATO-org/it-infrastructure-server/pull/24)へ0.1.0の導入設定を追加した。
config reloadと今回のPhase 2 contract auditはBridgeリポジトリだけを対象とする。

## Target evidence

| 対象 | 証拠 | 範囲 |
|---|---|---|
| Paper 26.2 build 126 | 固定API `io.papermc.paper:paper-api:26.2.build.126-stable`、[build API](https://fill.papermc.io/v3/projects/paper/versions/26.2/builds/126) | コンパイル、配布metadata・JARハッシュ確認。ユーザーによるBridge完全起動確認を追加 |
| Minecraft 26.2 | Paper JAR内`version.json` | Java 25、data pack 107.1、protocol 776。最新への読み替えなし |
| ValhallaMMO 1.10.3 | [固定配布JAR](https://cdn.modrinth.com/data/rxrgsoud/versions/GkeSDJSq/ValhallaMMO_1.10.3.jar)、plugin.yml、javap | 公開APIと`shouldPersist(Profile)`のbytecode確認 |
| ValhallaMMOソース | [既存checkoutと同じcommit](https://github.com/Athlaeos/ValhallaMMO/tree/231f9758d51a14bff2214d7c67428960b15648d4)、pom revision 1.10.3 | Registry、保存、Perk、respec経路の根拠。Bridgeへコピーしない |
| Gun Core V1.0.15 / Modern Guns V1.9.3 | 固定配布Data ZIPと公式metadataのSHA-512照合済み | [Phase 2 contract audit](phase2-contract-audit.md)。resource packと区別。実機未検証 |

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
| 完全起動 / Datapack handshake | ユーザー実機確認済み。handshake通過・FIREARMS登録まで到達 | ユーザー報告。marker不一致・停止など異常系の実機試験は未確認 |
| Skill / Profile登録 | ユーザー実機確認済み。FIREARMS登録成功 | DBの必要カラム・値の直接確認は未確認 |
| FIREARMS表示 | ユーザー実機確認済み。ValhallaMMOでFIREARMS / 銃器が認識・表示 | Perk取得・排他動作の確認とは別 |
| XP / Level Up | 入力制約、公開addEXP signature確認のみ | Lv0少量XPとLv1以上、実event・level値 |
| Profile save/load | 保存条件bytecode確認、Lv0 Profileの再ログイン維持をユーザー確認 | 特定XP値の保存一致とDB上の値は未確認 |
| Logout/Login | ユーザー実機確認済み。Lv0 FIREARMS Skill/Profileの維持 | 特定XP値・Perkの完全一致までは確認されていない |
| Server restart | 未確認 | 正常停止・完全再起動後の値とDB整合 |
| Perk取得 / 三択 | 設定構造・相互lockの静的テストのみ | 各選択の他2つ拒否、再接続後も排他 |
| respec / recalculation | 実機未確認。実装進行を優先して後回し（開発の停止条件にしない） | Valhalla標準respecで再選択・XP/level期待値 |
| 既存Skillへの副作用 | 必須Skill guardテストのみ | 他SkillのXP/level/Perkを前後比較 |
| Bridge config reload | ローカルのvalidation・権限・commandテスト成功。実機未確認 | console/管理者からのreload、旧設定保持、既存Profileとhandshakeの継続 |

### Bridge runtime config reload（2026-10-04）

`/firearms reload`を追加。`feato.gunvalhalla.reload`（既定OP）でconsoleからも実行でき、
`debug.enabled=false`やBridgeの非ready状態でもconfigの再読み込み自体は可能。
既存debugコマンドは別の`feato.gunvalhalla.debug`と有効なdebug設定を要求し、
互換性・ready検査とEXP入力制限を維持する。

対象はBridgeの`config.yml`にある全runtime設定。YAMLのparse・必須キー・boolean/numeric型・
有限値・範囲を検証してから不変の設定snapshotを一括差し替える。失敗時は旧設定とファイルを
維持し、送信者へ理由とWARNログを返す。成功時はdebug状態を表示し実行者名をログへ記録する。
YAMLエラーに含まれる設定内容は送信者やWARNへ出さない。

Skill/Profile/Registry、Perk tree、`firearms.yml`/`firearms_progression.yml`、
Datapack handshake・markerには触れない。MinecraftやValhallaMMOのreload、Pluginの
再有効化・自動再登録は行わない。停止済みPoCの復旧は完全再起動が必要。
reload経路のローカルテストは実機のPlayer/Valhalla動作を証明しない。

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
8. **後回し（未確認のまま開発継続）**。検証する際はテスト用プレイヤーのみ、Valhalla標準`/valhalla reset SKILLS_REFUND_EXP <player>`でrespec。
   標準仕様で他SkillのPerkもresetされるため、これは専用試験環境のみで実施。
   FIREARMS XP/level保持、三択lock解除、別Ability再取得と再計算を確認。
   単一skill resetも必要なら`/valhalla reset skill FIREARMS <player>`を使い別途結果を記録。
9. 正常logout、`stop`、完全再起動、同一UUID loginで値を再確認。
   既存Skillの変化を標準level-up/respecの期待動作と意図しない変化に分けて記録。
10. 新規検証ワールドでDatapack不在・marker不一致・必須Skillを1つずつ無効化し、
    FIREARMS登録停止のログを確認。既存データのある本番で異常系を実行しない。

永続化不能、登録不安定、データ破損、公開API経路不成立などの具体的な問題が
判明したら再現条件・ログ・DB状態を残す。respec/recalculationが未確認であることだけを
後続開発の停止理由にしない。respec専用処理や回避実装を推測で追加しない。

## Phase 2 (blocked after fixed artifact audit)

固定Gun Core V1.0.15 / Modern Guns V1.9.3のData ZIPを取得し公式SHA-512と照合した。
停止原因、functionと行番号、再現候補、transport候補、代替案と保守リスクは
[Phase 2 contract audit](phase2-contract-audit.md)を参照。

Native slowcastのentity hitはcaller markerを保持せずnearest markerからsource / statsを読み、
そのmarkerを削除する。公開damage hookはdamage / explosion生成後。
同位置・同時投射物で一意な帰属を保証できないため、最小wrapperからnative処理へ委譲する
設計を実装済みと扱わず、ユーザー指定のStop条件に従い停止した。
これは静的コードに基づく判断であり、Minecraftで混線を実測したとの主張ではない。

| Path / 機能 | 実装状態 |
| --- | --- |
| Raycast / actual shot / burst | built-in dispatchと追加弾経路を確認。adapter未実装 |
| Pellet / shotgun multi-target | type 3の既存経路を確認。shot ID未生成 |
| Slowcast | markerとstep経路を確認。native命中処理の一意なsource/cleanupが阻害要因 |
| Explosion attribution | native生成にshot/shooter/weapon UUIDなし。adapter未実装 |
| Datapack→Plugin transport | 同期record通知を検討。未採用・event loss保証なし |
| Logical shot / lifecycle / retention / debug | 未実装。入力、nearest、latest pollによる代用なし |

### Fixed 27-weapon matrix

固定loot tableの`gbg.idle_model` / projectile type / speed / fire rateからaudit scriptで生成した分類。
27銃すべてでIDがloot table名と一致した。武器対応表をJavaとDatapackへ手作業で二重登録しない。
shot creation / completionは全件未実装、実機確認は全件 **Not yet live-tested**。
`burst`はfire_rate 1..2のcrossbow経路候補、`pellet`はtype 3の複数target経路を表す。
分類は実機成功を意味しない。

| stable weapon ID | type / speed | path | shot creation / completion | 実機 |
| --- | --- | --- | --- | --- |
| `modern_guns:gun/aa12` | 3 / 1 | raycast / pellet | 未実装 | Not yet live-tested |
| `modern_guns:gun/ak47` | 1 / 1 | raycast | 未実装 | Not yet live-tested |
| `modern_guns:gun/barrett_m82` | 1 / 1 | raycast | 未実装 | Not yet live-tested |
| `modern_guns:gun/beretta_486` | 3 / 1 | raycast / pellet | 未実装 | Not yet live-tested |
| `modern_guns:gun/beretta_686` | 3 / 1 | raycast / pellet | 未実装 | Not yet live-tested |
| `modern_guns:gun/colt_python` | 1 / 1 | raycast | 未実装 | Not yet live-tested |
| `modern_guns:gun/desert_eagle` | 1 / 1 | raycast | 未実装 | Not yet live-tested |
| `modern_guns:gun/fn_scar` | 1 / 1 | raycast | 未実装 | Not yet live-tested |
| `modern_guns:gun/gl06` | 101 / 8 | slowcast | 未実装 | Not yet live-tested |
| `modern_guns:gun/glock_17` | 1 / 1 | raycast | 未実装 | Not yet live-tested |
| `modern_guns:gun/heckler_and_koch_mp5` | 1 / 1 | raycast / burst | 未実装 | Not yet live-tested |
| `modern_guns:gun/luger_p08` | 1 / 1 | raycast | 未実装 | Not yet live-tested |
| `modern_guns:gun/m1911` | 1 / 1 | raycast | 未実装 | Not yet live-tested |
| `modern_guns:gun/m202_flash` | 100 / 22 | slowcast | 未実装 | Not yet live-tested |
| `modern_guns:gun/m4a1` | 1 / 1 | raycast | 未実装 | Not yet live-tested |
| `modern_guns:gun/m72_law` | 100 / 12 | slowcast | 未実装 | Not yet live-tested |
| `modern_guns:gun/m79` | 101 / 6 | slowcast | 未実装 | Not yet live-tested |
| `modern_guns:gun/milkor_mgl` | 101 / 10 | slowcast | 未実装 | Not yet live-tested |
| `modern_guns:gun/p90` | 1 / 1 | raycast / burst | 未実装 | Not yet live-tested |
| `modern_guns:gun/remington_870` | 3 / 1 | raycast / pellet | 未実装 | Not yet live-tested |
| `modern_guns:gun/rpg_7` | 100 / 18 | slowcast | 未実装 | Not yet live-tested |
| `modern_guns:gun/sig_p320` | 1 / 1 | raycast | 未実装 | Not yet live-tested |
| `modern_guns:gun/smith_and_wesson_model_29` | 1 / 1 | raycast | 未実装 | Not yet live-tested |
| `modern_guns:gun/spas_12` | 3 / 1 | raycast / pellet | 未実装 | Not yet live-tested |
| `modern_guns:gun/springfield_m1a` | 1 / 1 | raycast | 未実装 | Not yet live-tested |
| `modern_guns:gun/thompson_submachine_gun` | 1 / 1 | raycast / burst | 未実装 | Not yet live-tested |
| `modern_guns:gun/winchester_model_70` | 1 / 1 | raycast | 未実装 | Not yet live-tested |

### ユーザー実機確認 / debug

現在の成果物はPhase 1用のまま。`debug.enabled=true`と`debug.shot-context=true`を
`/firearms reload`で読み直すことはできるが、shot logや`debug shots`は未実装。
Phase 2 debugを有効化できる完成品とは案内しない。
停止原因の再現候補は上記auditに記載。Phase 2 adapter成立後の受け入れでは、
AK-47 / M4A1 / Glock 17 / Barrett M82のhitとrange終了、MP5 / P90 / Thompsonの内部burst、
AA12 / Remington 870 / SPAS-12の1 triggerと複数target、GL06 / M79 / Milkor MGL /
M72 LAW / RPG-7 / M202 FLASHのmarkerから爆発まで、2人以上の同時射撃を検証する必要がある。
全27銃のcreation / stable ID / projectile path / completionとcleanupを確認し、
ユーザー実機合格後にのみPhase 2をCompleteへ変更する。

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

初期実装の両buildはBUILD SUCCESSFUL（JUnit 27件）。2026-10-04のconfig reload追加後も
`./gradlew clean build --no-daemon`はBUILD SUCCESSFUL。固定Valhalla JARの取得・SHA-512検証を含む。
JUnit: **66件、failures=0、errors=0、skipped=0**（既存27件と追加39件）。
既存の設定parse、登録前提（5必須Skill・重複・late registration）、heartbeat状態遷移、
互換性metadata、XP入力の異常系を維持。追加テストでは正常値・境界値、型/範囲/非有限値・
必須キー欠落、不正YAML/読込失敗での旧snapshot・ファイル保持、reload/debug権限分離、
debug無効時のreloadと各debug操作の拒否、状態切替、候補表示、WARN、秘密値非出力、plugin.ymlを検証。
2026-10-06のPhase 2 contract audit後も`./gradlew clean build --no-daemon`はBUILD SUCCESSFUL。
JUnit **66件、failures=0、errors=0、skipped=0**。release toolingの既存11テストも成功。
追加の固定artifact auditで27銃の分類と契約一致を確認し、異なるZIPのSHA-512拒否も検証した。
Shot Trackerの新規unit testは0件（adapter自体が停止条件により未実装）。

実際のrouterとファイルloaderをテストし、CommandSenderはテスト用proxy、debug操作は境界spyを使用。
これをValhalla runtimeや実プレイヤーの実機成功とは扱わない。
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
- ユーザー実機確認でhandshake・FIREARMS登録まで到達済み。SQL schemaの直接検査、異常系と保存値の整合は未確認。
- SQLite/MySQL/Redis各保存方式のDB値照合を伴う実機試験は未実施。MySQLの場合はcolumn metadataも確認する。
- Minecraft `/reload`、PlugMan、オンライン途中の追加はサポート対象外。Bridgeのconfigだけを読む`/firearms reload`は対応。公開Registry変更を検知したら停止。
- 固定Gun Core/Modern Guns Data ZIPと実行経路は調査済み。native slowcast attributionの停止原因はPhase 2 audit参照。
- respec/recalculation: **Deferred / non-blocking**。未確認のまま後回し。後続開発を止める条件にはせず、専用処理は追加しない。
- LICENSEは方針確定待ち。インフラ設定追加とユーザーの実機確認は上記参照。今回Codexによる本番適用は行わない。

## Manual release workflow (2026-10-03)

ユーザーの追加指示により`workflow_dispatch`のRelease workflowを追加。
`version`だけを指定し、専用metadata commit・annotated tag・両成果物のbuildと公開を行う。
release IDは全既存SemVer tagのmetadataとsourceから一意な連番を採番し、
PluginとDatapackの互換性markerを同期。対象依存versionとprotocolは変更しない。
11件のrelease toolingテストで入力・重複・採番・タグ再現性・成果物不一致を検証。
配布の自動化をPhase 1実機検証の成功とは扱わない。操作はREADMEのManual release参照。

release経路の検証: 一時コピーで`0.2.0-poc.99`をprepareし、
`./gradlew clean build --no-daemon`成功（Javaテスト27件）。
`release.py verify`でJAR内plugin.yml/bridge.propertiesとZIP内marker/pack.mcmetaの一致、
SHA256SUMS生成を確認。専用metadata commitへのannotated tag作成とtagged sourceを確認。
GitHub Actionsの式・workflow構文はactionlint 1.7.12で検査成功。
この試験versionはローカル検証のみで、GitHubにtag/Releaseを作成していない。
