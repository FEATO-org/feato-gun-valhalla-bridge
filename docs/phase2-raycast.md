# Phase 2 raycast adapter and native slowcast observations

2026-10-10 (Asia/Tokyo)。ユーザーの追加指示により、案A（raycast先行）と案E（計測）を実装。
Phase 1: Complete。Phase 2: **Raycast subset ready for live verification**。
Phase 2全体はCompleteでも全面Readyでもない。slowcast/explosion attributionは未実装、Phase 3はNot started。

## 導入と実際の操作

これは公開0.3.0に含まれない開発成果物。`bridge.properties`のversionを使うため、mainの開発版では
`0.1.0-poc.1`というファイル名になる。0.3.0へ手動リネームしない。
PluginとDatapackはこのbranchの同じbuildから両方を取得する。
transport契約変更に合わせ**protocol 2**とし、protocol 1の既存配布物との混在を拒否する。
GitHub Release / tag / release workflow / インフラ更新は今回行わない。

1. Java 25とPython 3を用意し、リポジトリ直下で`./gradlew clean build --no-daemon`。
2. 本番DBを使わない検証サーバーを正常停止し、World・Valhalla DB・銃itemをバックアップ。
3. `plugin/build/libs/feato-gun-valhalla-bridge-plugin-<version>.jar`と
   `plugin/build/distributions/feato-gun-valhalla-bridge-datapack-<version>.zip`を配置。
   ZIPを解凍せずワールドの`datapacks/`へ置く。旧Bridge JAR/ZIPと展開済み同namespaceを退避。
4. Minecraft 26.2 / Paper 26.2 build 126 / ValhallaMMO 1.10.3 /
   Gun Core V1.0.15 / Modern Guns V1.9.3で完全起動。
5. `debug.enabled: true`、`debug.shot-context: true`、`debug.damage-integration: false`へ変更。
   管理元とruntimeのconfigを揃え、consoleで`firearms reload`。
6. 起動ログのFIREARMS登録と`Raycast adapter armed`を確認。管理者権限を一般playerへ付与しない。
7. playerが試験銃をメインハンドに持ち、consoleまたは既存debug権限を持つ管理者から:

```text
firearms debug adapt <player>
firearms debug shots <player>
```

playerのchatから使う場合は先頭に`/`。`adapt`はオンライン完全一致名を使い、
**現在持っている1つの対応銃だけ**を適応する。自動inventory改変や全playerの常時適応はしない。
各銃へ持ち替えた後に再度adaptする。GL06等のslowcast銃はrefusedが期待値。

8. AK-47 / M4A1 / Glock 17 / Barrett M82でentity、block、何もない方向へ1発ずつ撃つ。
   `FIRED`、`ENTITY_HIT` / `BLOCK_HIT`、`COMPLETE`が同じIDとsessionで並ぶことを確認。
   headshotは既存native分岐を観測するだけで倍率変更しない。
9. MP5 / P90 / Thompsonで短い入力と連射を試し、native burstの追加弾ごとに別IDを確認。
10. AA12 / Remington 870 / SPAS-12で複数targetを狙い、1 triggerに1 ID、複数hitが同じIDとなることを確認。
11. 2人以上で別銃を持ち、近距離・同じtargetへ同時射撃。shooter UUID・weapon・IDの混線がないことを確認。
12. 射撃を止めて10tick以上待ち、`debug shots`のactiveが0になることを確認。
    50件を超える射撃後のrecent上限と、200tick後のrecent消滅も確認。
13. 観測用slowcast銃はadaptせず通常どおり使用する。RPG-7 / GL06等を単独・同時射撃し、
    `SHOT OBSERVATION`のmarker UUID、native_source、range、hit、creeper UUIDを記録する。
    このログはattribution=UNKNOWNであり、同じshotだと位置から結び付けない。
14. 終了前に銃を持つplayerについて`firearms debug restore <player>`を実行。
    restoreはそのplayerのmainhand/offhand/hotbar/inventoryにあるBridge適応itemを戻す。
    その後debugをfalseへ戻して`firearms reload`。

**重要:** 適応済みitemはsession付きで、別の起動sessionでは自動的に発射できない。
正常disableではオンラインplayerのinventoryを復元するが、offline player、container内、dropされた銃は
その処理の対象外。再ログイン後、銃をinventoryへ移してrestoreしてから再adaptする。
Pluginがない状態ではconsoleで`execute as <player> run function feato_gun_valhalla:shot/restore_inventory`
を使えるようBridge Datapackを保持する。Plugin/Datapackを外す前に復元すること。
停止したadapterの復旧には完全再起動が必要。Minecraft `/reload`、PlugManを使用しない。

## Actual shot callbackと既存挙動

Modern Guns固定loot tableから生成した21銃のallowlistに限り、実際の銃itemの
`gbg.projectile_type`を1→201、3→203へ適応する。`fgv.original`と`fgv.session`を保存する。
第三者の配布ZIP、loot table、functionは変更/overrideしない。ADSのitem_modelはIDに使わない。
武器のidle_model、original type、speed=1、damage typeが固定定義と一致しないitemはadaptしない。
予約typeは他custom raycast拡張との併用を未検証。

Gun Coreがammoを検査・消費して`gun/shooting_projectile`へ達したとき、
type >=200用の公開`custom_raycast`がBridgeを呼ぶ。
同じ経路を通る`player_loop`の追加burstも1回ずつcallbackとなる。
入力event、bow event、命中eventからFIREDを作らない。

callback中にshooter UUID、`gbg:gun_data gbg.idle_model`、original projectile、native source、
fire tick、実行位置をsnapshotする。Plugin受信成功のackがなければnative projectileへ委譲しない。
受信成功後、`gbg.projectile_type gbg.temp`を元の1/3へ戻して、固定nativeの
`gun/raycast/projectile/bullet` / `pellets`をそのまま呼ぶ。
native damage、range、piercing、resetのコードを複製しない。
shotgunは既存type 3の複数target経路であり、独立pellet entityの存在を仮定しない。

## Transport / security

Bridge専用storageは**同期macro引数のsnapshotにのみ**使用する。
macroはmarker生成前にUUIDの4整数、weapon等を実行commandの引数へ固定する。
1イベントにつき`execute summon marker`で1つのrecordを生成し、executorをそのrecordへ切り替える。
recordに`fgv.record`と現在sessionのscoreを付け、Paper公開`CommandSourceStack`で受信する。
Pluginは最新storageや後tickのnative storageを読まない。

受信はmain thread限定、executorがMarker、専用tag、現在sessionのscoreの一致が必須。
一般playerが`fgvnotify`を直接呼んでもplayer executorなので拒否する。
recordを作るfunction / summon / scoreboardの管理権限を一般playerへ付与しない。
sessionは暗号的秘密として扱わず、既存のserver管理権限を信頼境界とする。
他の管理権限を持つPlugin/Datapackによる意図的な偽造はこの境界の対象外。

Pluginは同期callbackをconsumeしてscoreとentityを直ちに削除。
Datapackのrecord runnerにもcleanupを置き、送信用macroの解釈失敗時も削除を実行する。
起動時の1回とchunk load時には中断された専用recordを掃除する。native slowcast markerは削除しない。
動的Plugin commandは初回Datapack parseより後に登録されるため、送信command自体もmacroにする。
固定Paperでこの初回起動順とmacroからの通知を確認した。

## Logical shot / lifecycle / retention

Plugin lifecycleに属する`ShotTracker`が単調増加longを生成。ログには起動sessionも出力する。
IDはsession内で一意。sessionを跨いで同じlongが現れ得るので、ログ比較にはsessionも含める。
発射情報・hit情報・debug snapshotを分離し、公開snapshotは不変。

`FIRED → IN_FLIGHT → ENTITY_HIT / BLOCK_HIT / UNKNOWN`。
native呼出しが戻った後にCOMPLETE。hitがなかった終了を即MISSとせずUNKNOWNとする。
command chain中断等でCOMPLETEが届かないcontextは10tickでEXPIRED。
同一tickに戻らないscope、入れ子FIRED、source変化、不正recordはadapter停止とする。
停止時は#ready=0でBridge適応銃をfail-closedにする。native処理の前にammoを消費するので、
停止中に撃つとammoだけ消費される場合がある。ログを確認してrestoreし、試験を止める。

完了contextは通常時即破棄。詳細debug時のみ直近50件、最大200tick。
active上限256、1shotのhit上限128。超過を黙って捨てず拒否する。
毎tickの全entity scan、全player×shot scan、disk IO、YAML read、reflectionは行わない。

## Native slowcast / explosion observations（案E）

Paperの公開entity add/remove eventでnative marker UUIDを観測し、観測したUUIDだけを次tick以降に調べる。
launch event時点ではnative scoresが未設定なのでnullの場合がある。
同じmarker UUIDのrangeが変化したらSTEP、消滅/除去時はREMOVEDを記録する。
最大128 marker、200tickで観測を終了。late-loaded markerもLAUNCHに見えるため、
このevent名は厳密なactual-launch証明ではない。

公開damage hookでは、その同期contextにあるnative sourceとtarget UUID / headshot分岐をsnapshotする。
raycast scope外ではENTITY_HIT / BLOCK_HITはattribution=UNKNOWN。
これには未適応raycastのhitも含まれ、slowcastだけだと断定しない。
native爆発tagのentity add/explodeをUUIDでログするが、marker / hitとのshot mappingは作らない。
位置が同じ、時刻が近いという理由で結び付けない。

案Bの結論: nativeのnearest source参照・marker削除は依然残る。
step wrapperだけでは置き換えられず、限定補正は**未採用 / 実機計測結果待ち**。
この計測でactual caller markerが命中hookへ渡るようになるわけではない。
native移動・命中処理を再実装する範囲へ広がる場合は、別途方針を確認する。

## Fixed internals / verification limits

内部名は`GunCoreContract`、Datapack専用shot/transport function、
[固定版audit](phase2-contract-audit.md)にまとめる。
buildは固定Gun Core / Modern Guns ZIPのSHA-512、呼び出すnative functionの存在、
生成allowlist、Bridge function/tag/JSON/markerを検証する。
allowlist更新は`scripts/generate_shot_contract.py <gun-core.zip> <modern-guns.zip>`、
検査は同じcommandへ`--check`。Java用propertiesとDatapack predicateを同じloot tableから生成する。
Python 3をbuild prerequisiteとする。
runtimeはnative objective、Bridge contract/sessionと、発射snapshotのweapon/type/sourceを検査。
これらの存在は実ロードされた外部packのversionを完全には証明しない。

固定Paperの隔離起動で、Bridge function読み込み、Phase 1登録、adapter arming、
同期macro→Pluginのblock record受信、連続recordの各通知と削除、
synthetic markerのLAUNCH/STEP/REMOVED、synthetic itemのtype 1→201→1復元を確認した。
最終transportでの連続通知・record消滅、不正recordによる#ready=0への停止とcleanupも確認した。
synthetic fixtureは実プレイヤーの銃撃、burst、shotgun、multiplayer成功の代用ではない。
全27銃はNot yet live-tested。射撃挙動の維持、一般playerからの偽造拒否、offline復元、
同時射撃等の実機受け入れは上記手順でユーザーが確認する。

固定Gun Coreの未変更`gbg:predicate/target.json`は26.2で`minecraft:type`のparse errorを出した。
固定ZIP内のfunctionからこのpredicateへの参照は見つからなかったが、pack全体の互換性成功とは扱わない。
またnative `crossbow_shoot`の`@p`による射手選択をBridgeは変更していない。
BridgeはGun Coreが実際に`shoot`を実行した射手を記録する。入力したplayerと違う射手が選ばれる
問題が実機で出た場合は、その条件を記録し推測補正しない。

ゲーム効果、XP自動付与、damage補正、ProtocolLib、NMS/private API、reflectionは追加していない。
