# 内容社区 Benchmark Spec

## 基准产品

- 国内：小红书、抖音精选内容页
- 全球：Pinterest、Instagram Explore

## 这轮收紧的规则

- 首页主视觉必须是内容流，不允许先出现 dashboard 风格策略卡。
- 主列表优先使用瀑布流/高低错落卡片，不使用死板等高门户网格。
- 首页要提供可点击的分类 tabs，并且能真实驱动 feed 过滤，而不是静态标签装饰。
- 顶栏应提供登录/注册入口或轻量账号流，不默认假设用户已经登录。
- 页面 body 内不允许再生成一套持久左侧栏或第二套 sticky 搜索条，壳层已提供导航与搜索。
- 辅助区只能做轻量支持信息，最多 1-2 个模块，不能抢主内容注意力。
- benchmark 名称、内部 contract 名称、用户原始 prompt 都不能直接出现在页面文案里。
- 卡片必须带创作者信息、话题/标签、点赞/评论/收藏等社区信号。
- 图片优先使用真实摄影 URL；当 mockData 缺图时，用真实感 seed media 自动补齐。
- `高收藏`、`实时热议`、`视频优先` 等筛选必须真实改变内容列表或排序结果。

## 数据契约补充

- feed item 推荐字段：
    - `title`
    - `description` / `summary`
    - `cover` / `image` / `thumbUrl`
    - `avatar` / `authorAvatar`
    - `author` / `creator` / `username`
    - `likes` / `likeCount`
    - `comments` / `commentCount`
    - `collects` / `saves`
    - `tags`
    - `location`
    - `time`
    - `page_route`

## QA 检查

- 首屏主列至少可见 6 张内容卡片。
- 主 feed 存在详情 handoff：`selectedItem = item; hash = '#detail'`。
- 分类 tabs、搜索和社交信号筛选要有真实 Alpine 状态，不允许只有静态文案。
- 顶栏应能看出未登录与已登录两种状态，至少有登录/注册触发入口。
- 页面源码中不允许残留 `placehold.co`、`dummyimage` 等占位图 URL。
- 页面要能从视觉上看出“社区首页”，而不是“资讯门户/后台看板”。
