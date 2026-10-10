# Phase 2: fixed artifact contract audit

調査日: 2026-10-06 (Asia/Tokyo)。当初判定: **Blocked / stop condition reached**。
2026-10-10にユーザーが案A/Eを承認し、[raycast先行Adapterと計測](phase2-raycast.md)を実装。
以下は当初調査の記録。slowcastの停止根拠は維持するが、raycastまで未実装という記述は当時の状態を表す。
Shot Context Adapterを実装済み、またはReady for live verificationとは判定しない。
本資料は固定配布物の静的調査であり、実Minecraftで混線を再現したとの主張ではない。

## 正本と再検証

配布元の固定ZIPを読み取り、Modrinthの固定version metadataのSHA-512と照合した。
resource packをDatapackとして扱っていない。第三者ZIPは変更せず、Bridgeへ同梱しない。

| 対象 | 固定配布物 | metadata |
| --- | --- | --- |
| Gun Core V1.0.15 | [Data ZIP](https://cdn.modrinth.com/data/Ti7LgRXJ/versions/X7knYm9t/Gun%20Core%20-%20Data%20V1.0.15.zip) | [X7knYm9t](https://api.modrinth.com/v2/version/X7knYm9t) |
| Modern Guns V1.9.3 | [Data ZIP](https://cdn.modrinth.com/data/ufgOyMFr/versions/bcKCNJp2/Modern%20Guns%20-%20Data%20V1.9.3.zip) | [bcKCNJp2](https://api.modrinth.com/v2/version/bcKCNJp2) |

SHA-512は`scripts/audit_shot_contract.py`の`ARTIFACTS`に固定した。
ZIPを別途取得し、リポジトリ直下から実行する:

```sh
python3 scripts/audit_shot_contract.py /path/to/gun-core.zip /path/to/modern-guns.zip
```

このread-only auditはハッシュ、burstの呼び出し、custom dispatch、問題のsource/cleanup選択、
爆発と公開hookの実行順、27銃のstable weapon ID・projectile pathを検査しJSONを出力する。
成功exitは「固定版の静的調査と一致」を意味し、adapterや実機検証の成功を意味しない。
既存Gradle buildへ第三者Datapackの新規ダウンロード依存は追加していない。

## 実コードで確認した契約

以下の行番号は上記Gun Core ZIP内の`data/gbg/function/`配下。内部依存の記録をここへ集約する。

| function | 行 | 契約 / 問題 |
| --- | --- | --- |
| `gun/shoot` | 1以降 | ammoを検査してから発射。`gbg:gun_data`へ武器statsを同期snapshot。`gbg:macro input.source`は射手の`gbg.id`でありshot IDではない |
| `player_loop` | 17 | `gbg.burst_duration=2`で`gun/shoot`を直接呼ぶ。crossbow入力callbackだけでは追加弾を取得できない |
| `gun/shooting_projectile` | 3–26 | built-in typeを直接dispatch。`custom_raycast`はtype >=200かつspeed <2のみ |
| `gun/slowcast/launch` | 2–8 | markerをsummon後、`gbg.spawning_in`のmarkerを選び`launch2`を実行する |
| `gun/slowcast/launch2` | 2, 12–15 | markerの射手ID、teleport、射手tag除去は`@p[tag=gbg.gun_shooter]`。caller UUIDを渡していない |
| `gun/slowcast/temp_tick` | 2 | 全`gbg.slowcast` markerについてstepを実行。slowcastのlaunchごとのcallbackではない |
| `gun/slowcast/sort` | 7–17 | built-in 100–104を直接dispatch。`custom_slowcast`はtype >=200のみ |
| `gun/slowcast/projectile/rocket` | 9–13 | block / range終端 / entity命中でnative hit functionを呼ぶ |
| `gun/slowcast/projectile/grenade` | 8–11 | native hit functionを呼ぶ。entity hitでは`as`でexecutorがtargetへ切り替わる |
| `gun/slowcast/hit_entity` | 2–6, 11 | source、名前、damage/headshot damage、damage typeは`@e[type=marker,tag=gbg.slowcast,sort=nearest,limit=1]`から読む |
| `gun/slowcast/hit_entity` | 19–24 | explosion生成→公開`damage_type_entity` hook→nearest marker削除の順。命中したmarker UUIDを渡さない |
| `gun/slowcast/hit_block` | 2–11 | damage/nameは`@s`から読むが、damage typeはnearest marker。explosion生成後に公開hook |
| `gun/shot/explosion` | 2 | creeperへshot/shooter/weapon UUIDを保存しない。生成したUUIDを公開hookへ返さない |
| `gun/shot/safe_explosion` | 3 | 同じcreeper生成方式。別途mob_griefing変更とscheduleあり |
| `gun/raycast/hit_entity` | 2, 16–21 | headshot判定の分岐あり。公開hook後に`@p`の距離scoreを更新。type 3の複数targetでもlogical shot開始通知なし |
| `gun/raycast/reset` | 2–4 | hit tag除去、`@p`のshooter tag除去、全playerのraycast distance reset |

全27銃の`gbg.projectile_type`は1、3、100、101で、custom hook条件を満たさない。
`gbg.idle_model`は全27銃でloot tableの銃IDと一致。stable IDの候補として確認済み。
type 3は既存のpellet raycast / 複数target経路であり、独立pellet entityの存在を仮定しない。

## Stop判定と再現候補

**現在の「最低限のwrapperから既存slowcastへ委譲する」設計では、命中元markerの一意性を保証できない。**
Native entity-hitはcaller markerとの識別子比較をせずnearest markerを参照する。
同距離に複数markerがある場合、どのmarkerが選ばれてもcallerと一致する契約はない。
公開hookから元markerを推測で復元したり、nearest creeperを関連付けたりして成功扱いにはできない。

専用試験環境での再現候補（未実施）:

1. 固定Paper 26.2 build 126 / Minecraft 26.2 / Gun Core V1.0.15 / Modern Guns V1.9.3を使用する。
2. 2人でRPG-7とGL06等の異なる武器を、近距離から同じtarget / blockへ同時に発射する。
   投射物の経路が重なるようにし、命中直前の複数marker UUID・座標・`gbg.id`・damage typeを記録する。
3. 実行中markerと、native hit functionが選ぶnearest markerが一致するか確認する。
   同位置・同距離のケース、片方がもう一方より命中位置に近いケースを含める。
4. native damageのsource、爆発名、marker消滅を比較する。既存creeperも同じ場所に置いて識別条件を確認する。
5. 混線が実測されたらtick / player UUID / weapon / marker UUID / 座標 / functionを記録する。
   再現しない場合もnearest selectorが一意なID契約へ変わったことにはならない。

`launch2`の`@p`も調査対象だが、通常の単一実行で常に誤るとは判定していない。
明確な停止根拠はhit時にcaller identityが失われ、source参照とcleanupがnearestへ戻ること。
これはユーザー指定の「slowcast attributionが一意にできない」「複数playerでsourceが混ざる」
停止条件に対応する。先にunsafe adapterを導入して実機へ進めない。

## 検討したadapter / transport

- Raycast: runtimeの銃itemをBridge管理type >=200へ適応し、original typeとidle_modelを保持して
  `custom_raycast`から既存projectile functionへ委譲する候補はある。配布Datapackの直接編集なしで
  actual shot / burst callbackを得る可能性があるが、item状態変更・shoot_types・reset・他拡張との
  相互作用を未検証。単にtagを追加するだけでは既存27銃へ届かない。
- Slowcast: 同様のtype適応とmarker UUIDによるlaunch/step識別は候補だが、native projectileへ
  委譲すると上記hit functionのnearest参照・削除が残る。step wrapperだけで修正できない。
- Transport候補: callback内でBridge専用recordへsnapshotし、同期内部commandで1件ずつ渡す。
  player senderの拒否、信頼するserver context、session、consume処理の検証が必要。
  今回はcallback契約が成立していないため未採用・未実装。共有latest storageのpollは行わない。
- Explosion: 命中tagはcreeper生成後。同期spawn eventとBridgeのscopeを組み合わせる可能性はあるが、
  元slowcast contextが正確でなければ誤った帰属を記録する。Paper spawn event単独を正本にはしない。
- Logical shot ID / lifecycle / TTL / debug: ランタイム未実装。IDを入力eventやhitから捏造しない。
  `debug.shot-context`は予約設定のまま。`/firearms debug shots`は存在しない。

「Plugin側trackerのunit testが通る」だけでは上記欠落を補えないため、
未接続のShot DTO / trackerやmockによる成功テストは追加していない。
FIREARMSゲーム効果、ProtocolLib、NMS、reflection、第三者function overrideは追加していない。

## 代替案と保守リスク

| 代替案 | 必要な変更 / リスク |
| --- | --- |
| Gun Core作者によるactual-shot / launch / hit / explosion callback追加 | caller shooter/markerと生成creeper UUIDを明示して渡す。最も契約を明確にできるが、固定V1.0.15の範囲外。現在の指示では進めない |
| Bridge側でslowcast projectile / hitの該当経路を実装し直す | exact marker contextを保ったままdamage/explosionへ渡す必要がある。薄いwrapperを超え、native engine挙動の複製・更新追従・headshot/pierce/range検証が必要。今回の「独自engineを避ける」方針との調整が必要 |
| Gun Core functionの改変 / override | caller identityを保持できるようにできるが、明示禁止。採用しない |
| markerの一時移動 / 他markerのtagやscoreを隠してnearest選択を操作 | 他projectile・schedule・他拡張への副作用、異常終了時の復元が問題。正確性の根拠として採用しない |
| ProtocolLibによる入力観測 | server burst / markerのnative hit選択 / creeper UUID問題を解決しない。依存追加しない |

## Build / verification

固定ZIPを使用したaudit: 成功、27銃を分類。全銃 **Not yet live-tested**。
Plugin/Datapackのランタイム変更はなく、Phase 1を維持する。
既存buildの結果は`docs/poc.md`に記録する。Release / tag / release workflowは実行しない。
