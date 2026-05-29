# 弹幕管理列表头像不显示

**已应用到本机后端：** `/Users/zjs/Downloads/easylive-main/easylive`（`VideoDanmu.java` + `VideoDanmuMapper.xml`）

修改后请 **重新编译并重启 easylive-web**（7071 端口），再在 App 里打开弹幕管理；`loadDanmu` 响应里应出现 `"avatar":"cover/..."`。

---

## 原因

`UcenterInterActionController.loadDanmu` 虽设置了 `queryVideoInfo=true`，但 `VideoDanmuMapper.xml` 的联表查询**只查了昵称，没有查头像**：

```sql
-- 当前（缺 avatar）
,vd.video_name videoName, vd.video_cover videoCover, u.nick_name nickName

-- 评论列表（有 avatar）
,vi.video_name videoName, vi.video_cover videoCover, u.nick_name nickName, u.avatar avatar
```

且 `VideoDanmu.java` 实体类也**没有 `avatar` 字段**，即便改 SQL 也无法序列化到 JSON。

评论管理正常，是因为评论 Mapper 已包含 `u.avatar`。

## 修改（easylive-common）

### 1. `VideoDanmu.java` 增加字段

```java
private String avatar;

public String getAvatar() {
    return avatar;
}

public void setAvatar(String avatar) {
    this.avatar = avatar;
}
```

### 2. `VideoDanmuMapper.xml` 的 `selectList`

将：

```xml
,vd.video_name videoName,vd.video_cover videoCover,u.nick_name nickName
```

改为：

```xml
,vd.video_name videoName,vd.video_cover videoCover,u.nick_name nickName,u.avatar avatar
```

重启 `easylive-web` 后，App 端 `loadDanmu` 返回的每条弹幕会带 `avatar`，与评论列表一致。

## App 端兜底（已实现）

若暂未改后端，Android 在拉取弹幕列表后会根据 `userId` 调用 `ucenter/home/getUserInfo` 补全头像（内存缓存，同一用户只请求一次）。你日志里 15 条弹幕均为 `userId=5070056780`，只会多 1 次用户信息请求。
