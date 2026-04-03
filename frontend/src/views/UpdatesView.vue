<template>
  <div class="min-h-screen text-white selection:bg-cyan-500/30 overflow-x-hidden">
    <!-- Navbar (Consistent) -->
    <nav
        class="fixed top-0 w-full z-50 px-8 py-6 flex justify-between items-center glass-morphism">
      <div class="flex items-center gap-4 group cursor-pointer" @click="$router.push('/')">
        <div class="w-10 h-10 flex items-center justify-center">
          <svg class="h-9 w-auto logo-heartbeat text-white fill-current" viewBox="0 0 100 100">
            <rect height="50" rx="6" width="80" x="10" y="25"/>
            <path d="M25 35 V65 H38 V35 L55 65 V35 H68 V65" fill="none" stroke="black" stroke-linecap="square" stroke-width="8"/>
          </svg>
        </div>
        <span class="text-xl font-black tracking-tighter uppercase italic text-white">LingNow</span>
      </div>

      <div class="hidden md:flex gap-8 text-sm font-medium text-gray-400">
        <router-link class="hover:text-white transition-colors" to="/solutions">{{
            currentLang === 'cn' ? '解决方案' : 'Solutions'
          }}
        </router-link>
        <a class="hover:text-white transition-colors cursor-pointer"
           @click="store.isPricingOpen = true">{{ t('nav.pricing') }}</a>
      </div>

      <div class="flex gap-4 items-center">
        <div class="hidden lg:flex gap-6 text-xs font-bold text-gray-500 tracking-widest mr-4">
          <router-link class="hover:text-cyan-400 transition-colors" to="/docs">{{ t('nav.docs') }}</router-link>
          <router-link class="hover:text-cyan-400 transition-colors" to="/updates">{{ t('nav.updates') }}</router-link>
        </div>
        <!-- i18n Dropdown -->
        <div class="relative group mr-2">
          <button
              class="flex items-center gap-1.5 px-3 py-1.5 rounded-full border border-white/10 glass-morphism text-xs font-bold transition-all hover:border-white/30">
            <span>{{ currentLang === 'cn' ? '🇨🇳' : '🇺🇸' }}</span>
            <span class="text-gray-300 uppercase">{{ currentLang }}</span>
          </button>
          <div
              class="absolute right-0 mt-2 w-32 glass-morphism rounded-xl p-1.5 opacity-0 invisible group-hover:opacity-100 group-hover:visible transition-all">
            <button class="w-full text-left px-3 py-2 rounded-lg hover:bg-white/5 text-xs" @click="setLang('cn')">
              🇨🇳 中文
            </button>
            <button class="w-full text-left px-3 py-2 rounded-lg hover:bg-white/5 text-xs" @click="setLang('en')">
              🇺🇸 English
            </button>
          </div>
        </div>
        <button class="px-5 py-2 bg-white text-black rounded-full text-sm font-bold hover:bg-cyan-400 transition-all"
                @click="$router.push('/workbench')">
          {{ currentLang === 'cn' ? '启动' : 'Launch' }}
        </button>
      </div>
    </nav>

    <div class="max-w-4xl mx-auto pt-48 pb-32 px-8">
      <div
          class="inline-flex items-center gap-2 px-3 py-1 rounded-full border border-white/10 bg-white/5 text-[10px] font-bold tracking-widest text-[#9D00FF] uppercase mb-8">
        {{ t('updates.badge') }}
      </div>
      <h1 class="text-5xl font-black mb-16 tracking-tighter leading-tight" v-html="t('updates.hero_title')"></h1>

      <div class="space-y-16 relative">
        <!-- Vertical Timeline Line -->
        <div class="absolute left-6 top-0 bottom-0 w-px bg-white/5"></div>

        <div v-for="(log, idx) in logList" :key="idx" class="relative pl-20 group">
          <!-- Timeline Node -->
          <div
              class="absolute left-[20px] top-4 w-2.5 h-2.5 rounded-full bg-cyan-500 shadow-[0_0_10px_rgba(0,209,255,0.8)] border-2 border-[#050505] group-hover:scale-125 transition-transform z-10"></div>

          <div class="text-[10px] font-black text-gray-600 mb-2 uppercase tracking-[0.2em]">
            {{ t(`updates.logs.[${idx}].date`) }}
          </div>
          <div class="glass-morphism rounded-3xl p-8 border border-white/10 group-hover:border-white/20 transition-all">
            <h3 class="text-2xl font-bold mb-4 tracking-tight group-hover:text-cyan-400 transition-colors">
              {{ t(`updates.logs.[${idx}].title`) }}
            </h3>
            <p class="text-gray-400 leading-relaxed text-sm mb-6">
              {{ t(`updates.logs.[${idx}].desc`) }}
            </p>

            <div class="flex flex-wrap gap-2">
              <span v-for="tag in t(`updates.logs.[${idx}].tags`)" :key="tag"
                    class="px-3 py-1 rounded-full bg-white/5 border border-white/5 text-[8px] font-black text-gray-500 uppercase tracking-widest">{{
                  tag
                }}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="mt-32 text-center">
        <p class="text-gray-600 text-xs font-bold uppercase tracking-widest mb-4 italic">{{ t('updates.end_msg') }}</p>
        <button class="px-6 py-2 rounded-full border border-white/10 hover:bg-white/5 transition-colors text-[10px] font-black uppercase tracking-widest"
                @click="$router.push('/')">
          {{ t('updates.cta_back') }}
        </button>
      </div>
    </div>
    <BrandFooter />
  </div>
</template>

<script setup>
import {onMounted, ref} from 'vue'

const currentLang = ref(localStorage.getItem('lang') || 'cn')

const setLang = (lang) => {
  currentLang.value = lang
  localStorage.setItem('lang', lang)
}

const logList = [{}, {}, {}] // Iteration base

const translations = {
  en: {
    nav: {platform: 'Platform', solutions: 'Solutions', pricing: 'Pricing', docs: 'Docs', updates: 'Updates'},
    updates: {
      badge: 'Evolution Log',
      hero_title: 'Agent Network <br/> <span class="text-gray-500 italic">Weekly Iterations</span>',
      end_msg: 'End of timeline for this month',
      cta_back: 'Back to hub',
      logs: [
        {
          date: 'April 2026, Week 2',
          title: 'Industrial Snap v8.0 Deployment',
          desc: 'We refined the multi-page portal architecture and implemented a new vertical wrapping grid for industry showcase. The agent audit network now supports two-phase commits for code safety.',
          tags: ['Core Infrastructure', 'Aesthetics', 'Routing']
        },
        {
          date: 'April 2026, Week 1',
          title: 'Expert Logic Migration',
          desc: 'Migrating hardcoded prompt strategies to a centralized Agent Constitution. Improved UI accuracy by 32% for healthcare and SaaS fintech modules.',
          tags: ['Agents', 'Intelligence']
        },
        {
          date: 'March 2026, Week 4',
          title: 'Multilingual Foundation Fix',
          desc: 'Seamless integration of CN/EN flag-based switching. Optimized the internationalization pipeline for production grade portals.',
          tags: ['i18n', 'Globalization']
        }
      ]
    }
  },
  cn: {
    nav: {platform: '平台', solutions: '解决方案', pricing: '定价', docs: '文档', updates: '动态'},
    updates: {
      badge: '演进日志',
      hero_title: '智能体网络 <br/> <span class="text-gray-500 italic">周度迭代报告</span>',
      end_msg: '本月时间轴更新完毕',
      cta_back: '返回中心',
      logs: [
        {
          date: '2026年4月，第2周',
          title: '工业级快照 v8.0 部署',
          desc: '我们完善了多页面门户架构，并为行业展示实现了全新的垂直包裹网格。智能体审计网络现在支持两阶段代码安全提交。',
          tags: ['核心基座', '审美评分', '路由优化']
        },
        {
          date: '2026年4月，第1周',
          title: '专家逻辑迁移',
          desc: '将硬编码的提示策略迁移到中央智能体准则 (Agent Constitution)。医疗健康和金融科技模块的 UI 还原度提升了 32%。',
          tags: ['智能体协同', '规则引擎']
        },
        {
          date: '2026年3月，第4周',
          title: '多语言基座修复',
          desc: '无缝集成基于国旗标志的中英切换。优化了生产级门户的国际化渲染管线。',
          tags: ['国际化', '全球化']
        }
      ]
    }
  }
}

const t = (path) => {
  const keys = path.split('.')
  let res = translations[currentLang.value]
  for (const key of keys) {
    if (key.includes('[') && Array.isArray(res)) {
      const index = parseInt(key.match(/\d+/)[0])
      res = res[index]
    } else {
      res = res ? res[key] : null
    }
  }
  return res || path
}

onMounted(() => {
  const saved = localStorage.getItem('lang')
  if (saved) currentLang.value = saved
})
</script>
