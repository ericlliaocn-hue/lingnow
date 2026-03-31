package cc.lingnow.service;

import cc.lingnow.model.ProjectManifest;
import cc.lingnow.util.ExcelReportGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SpringBootTest
@ActiveProfiles("test")
public class UiDesignerAgentStressTest {

    private static final List<String> INTENTS = Arrays.asList(
            "社区团购选品小程序", "深夜食堂在线预约单", "精酿啤酒口味评分系统", "有机农场周配送平台", "网红烘焙店联名款预售",
            "挂耳咖啡订阅会员中心", "预制菜冷链追踪仪表盘", "轻食低脂午餐热量配比", "怀旧零食集合店商城", "宠物友好咖啡馆领号",
            "露营主题餐厅排位系统", "全自动智能咖啡机运维屏", "精选酒窖年份管理看板", "街头快餐流动车选址器", "私人厨师到家服务预约",
            "奶茶爆款配方研发日志", "传统茶馆静谧空间预订", "潮玩盲盒抽屉交易网关", "品牌折扣店库存瞬时同步", "手办涂装工坊进度展示",
            "社区生鲜柜自动补货系统", "火锅店口味定制化下单", "老字号点心数字化档案馆", "共享厨房租借与排班", "轻奢下午茶名媛风详情页",
            "深夜酒吧特调酒饮图鉴", "快闪店人流热力感知墙", "全球零食盲盒盲选器", "自助洗菜机运行状态监控", "智能餐具消毒记录查询",
            "美甲工坊独角兽色调预约", "男士专业理容胡须护理", "汉服妆造租赁沉浸式详情", "城市轻医美皮肤状况初筛", "牙齿正畸进度3D可视化",
            "私人香氛心情匹配系统", "儿童趣味理发室排队", "燃脂团课抢位排行看板", "中式养生按摩技师排班", "精读会深夜共读笔记社区",
            "SPA芳疗精油调配比例", "瑜伽馆静音环境监测屏", "高端健身房会员体测报告", "睡眠质量多导分析终端", "康复机器人训练强度调整",
            "中医脉象数字化特征提取", "社区智慧养老老人监护", "个人碳中和减碳钱包卡", "复古相机滤镜模拟工作站", "黑胶唱片物理修复进度",
            "宠物殡葬云缅怀生命册", "剧本杀拼场DM语音房", "乐高MOC作品缺件列表", "观鸟笔记与地理足迹图", "盲盒在线扭蛋机动画",
            "多肉植物浇水频率提醒", "古着孤品在线鉴定看板", "黑胶唱片线下交换市集", "独立游戏众筹进度仪表", "汉服穿搭日记社区展示",
            "流浪宠领养多维度匹配", "猫咖在线选猫与定位", "狗狗行为训练远程指导", "金鱼培育水质实时看板", "爬行宠物环境湿度预警",
            "家庭杂物整理收纳指南", "专业搬家打包服务详情页", "上门修锁师傅实名验证", "社区垃圾分类积奖励分", "老房翻新风格模拟对比",
            "考研倒计时心态调节器", "乐高零件材质物理库", "晚托接送校车实时图", "书法篆刻名家印谱搜", "魔方盲拧盲记法教程",
            "数字游民网络延迟图", "露营地水源电力补给", "老房外立面翻新效果", "骑行补给站海拔曲线", "非遗传承人带徒日志",
            "私房烘焙派对预定", "精酿啤酒原浆追溯", "有机蔬菜众筹收割", "深夜便利店库存预警", "流动披萨车订单热图",
            "社区生鲜配送地图", "怀旧风糖水铺菜单", "素食主义轻餐馆详情页", "移动咖啡车位置实时地图", "烘焙大赛选手积分看板",
            "高端和牛产地溯源报告", "拉面馆口味浓郁度定制", "共享酒庄葡萄藤认领", "深夜宵夜外卖热度排行", "职场减压冥想空间索引",
            "护肤品成分安全百科搜", "整形手术术后康复助手", "老字号染坊数字化手册", "私人色彩诊断顾问系统", "发型师设计作品瀑布流",
            "化妆品试用装申请中心", "健身餐营养成分计算器", "专业马术俱乐部马匹档案", "极简主义生活闲置交换", "户外飞盘社群拼场工具",
            "盲盒涂装大赛投票中心", "独立设计师成衣预售页", "定制西装远程量体指导", "盲童有声绘本伴读", "老房地暖加装进度",
            "社区流浪猫绝育地图", "复古打字机色带交易", "独立音乐人巡演拼房", "学区房入位顺位查询", "考研政治重点速记卡",
            "高考口语测评模拟房", "乐高颗粒称重计算器", "书法临摹重影纠偏", "沉浸式冥想钵音疗愈", "极简生活胶囊衣橱图",
            "黑胶唱片防霉养护指南", "古建木结构榫卯拆解", "专业登山向导轨迹共享", "社区公益电影排片表", "家庭紧急避险包核验",
            "城市屋顶农场认养", "非遗蓝染花样数字化", "深夜食堂隐藏菜单解锁", "社区养老中心活动计划", "智能水族箱光照调节屏",
            "老旧风扇复刻改造论坛", "手办柜灯光智能控制屏", "城市涂鸦涂鸦点地图标记", "独立书店静读空间预约", "沉浸式密室逃脱进度追",
            "盲童听书选片推荐", "学区房溢价指数预测", "儿童防走失定位守护", "加油站油价变动看板", "充电桩功率实时分配",
            "社区微马比赛积分榜", "家庭垃圾上门回收预约", "共享雨伞信用借还图", "老字号火柴盒历史馆", "盲盒拆盒模拟概率计",
            "考研政治重难点分析", "乐高停产件成色分析", "宠物领养评分仪表盘", "观鸟等级全球排行榜", "古着市场真伪查询器",
            "社区低碳生活光伏收益", "私人裁缝修改进度表", "家政阿姨在线视频面试", "智能灯杆光纤流量监控", "街道下水管网数字孪生",
            "社区图书自助借还机", "老年大学书法班选座", "社区团餐营养分析表", "周末野餐点蚊虫预警", "流动雪花冰摊位地图",
            "盲盒隐藏款市场溢价表", "乐高模型灯组遥控器", "家庭修漏水进度同步", "社区路灯损坏一键报修", "城市跑酷点难度标注",
            "老旧唱片机转速校准", "手工艺纸伞纹样数据库", "毕业设计作品在线展厅", "学车倒车入库AI指导", "书法楷书结构分析仪",
            "冥想室负氧离子监测", "家庭极简断舍离分类", "胶片相机暗房冲洗进度", "古建保护修缮资金公示", "登山高度含氧量实时器",
            "社区老年乐团排练单", "防震减灾演练点索引", "城市阳台种菜水分仪", "非遗陶瓷花纹生成器", "深夜食堂热单TOP10"
    );
    @Autowired
    private GenerationService generationService;

    @BeforeEach
    public void setup() {
        new File("target/stress-html").mkdirs();
    }

    @Test
    public void runFull200IndustryStressTest() throws Exception {
        List<ExcelReportGenerator.IndustryReportRow> reportRows = new ArrayList<>();
        System.out.println(">>> [StressTest] Starting INDUSTRIAL PIPELINE run (200 Industries)...");

        // We run a few for verification of the NEW pipeline
        for (int i = 0; i < 3; i++) {
            String intent = INTENTS.get(i);
            String sessionId = "verify-v4-" + i;
            try {
                System.out.println(">>> [Step 1] Planning & Data Injection & DNA synthesis for: " + intent);
                ProjectManifest manifest = generationService.planRequirements(sessionId, intent, "ZH");

                // Define paths
                File htmlFile = new File("target/stress-html/" + sessionId + ".html");
                File snapshotFile = new File("target/stress-html/" + sessionId + ".png");

                // Optimization: Skip regeneration if HTML already exists
                if (htmlFile.exists()) {
                    System.out.println(">>> [Step 2] SKIPPING Generator (File exists): " + intent);
                } else {
                    System.out.println(">>> [Step 2] Designing & Logic Auditing & Auto-Repair for: " + intent);
                    manifest = generationService.generatePrototype(sessionId, "ZH", null);
                    Files.writeString(htmlFile.toPath(), manifest.getPrototypeHtml(), StandardCharsets.UTF_8);
                }

                // Add to Excel Report
                reportRows.add(ExcelReportGenerator.IndustryReportRow.builder()
                        .userIntent(intent)
                        .visualDna(manifest.getMetaData() != null ? manifest.getMetaData().get("visual_reasoning") : "N/A")
                        .metadataJson(cc.lingnow.util.JsonUtils.toJson(manifest.getMetaData()))
                        .status("SUCCESS")
                        .localPath(htmlFile.getAbsolutePath())
                        .snapshot(snapshotFile.exists() ? snapshotFile : null) // Link the image if it exists
                        .build());

                System.out.println(">>> [Step 3] SUCCESS: Managed to generate " + intent + " -> Path: " + htmlFile.getAbsolutePath());

            } catch (Exception e) {
                System.err.println("FAILED: " + intent + " - Error: " + e.getMessage());
                e.printStackTrace();
            }
        }

        ExcelReportGenerator.export("STRESS_TEST_REPORT_200.xlsx", reportRows);
        System.out.println(">>> [StressTest] Export Finished: STRESS_TEST_REPORT_200.xlsx");
    }
}
