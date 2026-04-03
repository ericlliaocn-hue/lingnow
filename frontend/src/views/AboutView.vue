<template>
  <div class="min-h-screen tech-grid text-white selection:bg-cyan-500/30 overflow-x-hidden">
    <!-- Navbar (Consistent) -->
    <nav
        class="fixed top-0 w-full z-50 px-8 py-6 flex justify-between items-center glass-morphism border-b border-white/5">
      <div class="flex items-center gap-2 cursor-pointer" @click="$router.push('/')">
        <img alt="Logo" class="w-8 h-8 object-contain" src="/logo-icon.png"/>
        <span class="text-xl font-bold tracking-tight text-white">LingNow</span>
      </div>

      <div class="hidden md:flex gap-8 text-sm font-medium text-gray-400">
        <router-link class="hover:text-white transition-colors" to="/solutions">
          {{ currentLang === 'cn' ? '解决方案' : 'Solutions' }}
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
          class="inline-flex items-center gap-2 px-3 py-1 rounded-full border border-white/10 bg-white/5 text-[10px] font-bold tracking-widest text-cyan-400 uppercase mb-8">
        {{ t('about.mission_badge') }}
      </div>
      <h1 class="text-7xl font-black mb-12 tracking-tighter leading-none italic" v-html="t('about.hero_title')"></h1>

      <div class="glass-morphism rounded-[2.5rem] p-12 border border-white/10 space-y-12">
        <section>
          <h2 class="text-2xl font-bold mb-6 text-cyan-400 tracking-tight">{{ t('about.vision_title') }}</h2>
          <p class="text-xl text-gray-400 leading-relaxed font-light">
            {{ t('about.vision_desc') }}
          </p>
        </section>

        <div class="grid grid-cols-1 md:grid-cols-2 gap-8 py-8 border-y border-white/5">
          <div class="p-6 rounded-3xl bg-white/5 border border-white/5">
            <div class="text-3xl font-black mb-2">99.9%</div>
            <div class="text-[10px] text-gray-500 uppercase font-black tracking-widest">{{
                t('about.stat_fidelity')
              }}
            </div>
          </div>
          <div class="p-6 rounded-3xl bg-white/5 border border-white/5">
            <div class="text-3xl font-black mb-2">4+</div>
            <div class="text-[10px] text-gray-500 uppercase font-black tracking-widest">{{
                t('about.stat_agents')
              }}
            </div>
          </div>
        </div>

        <section>
          <h2 class="text-2xl font-bold mb-6 text-purple-400 tracking-tight">{{ t('about.factory_title') }}</h2>
          <p class="text-lg text-gray-500 leading-relaxed">
            {{ t('about.factory_desc') }}
          </p>
        </section>
      </div>

      <div class="mt-20 flex justify-center gap-8">
        <button class="text-gray-500 hover:text-white transition-colors text-sm font-bold uppercase tracking-widest"
                @click="$router.push('/docs')">
          {{ t('about.cta_docs') }}
        </button>
        <button class="text-gray-500 hover:text-white transition-colors text-sm font-bold uppercase tracking-widest"
                @click="$router.push('/')">
          {{ t('about.cta_home') }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import {onMounted, ref} from 'vue'

const currentLang = ref(localStorage.getItem('lang') || 'cn')

const setLang = (lang) => {
  currentLang.value = lang
  localStorage.setItem('lang', lang)
}

const translations = {
  en: {
    nav: {platform: 'Platform', solutions: 'Solutions', pricing: 'Pricing', docs: 'Docs', updates: 'Updates'},
    about: {
      mission_badge: 'The Mission',
      hero_title: 'Revolutionizing <br/> Software Production',
      vision_title: 'Our Vision',
      vision_desc: 'LingNow was born from a simple yet radical idea: What if we could automate the most creative parts of software development? Our team of AI researchers and developers is building a world where complex architectures are architected, designed, and audited by specialized AI agents in seconds.',
      stat_fidelity: 'UI Fidelity Goal',
      stat_agents: 'Specialized Agent Roles',
      factory_title: 'The Factory Model',
      factory_desc: "We don't provide a tool; we provide a factory. LingNow's multi-agent network mimics human teamwork, ensuring that design, security, and performance are never compromised for speed. Join us on our journey to industrialize the digital future.",
      cta_docs: 'Read documentation',
      cta_home: 'Back to home'
    }
  },
  cn: {
    nav: {platform: '平台', solutions: '解决方案', pricing: '定价', docs: '文档', updates: '动态'},
    about: {
      mission_badge: '核心使命',
      hero_title: '重塑 <br/> 软件生产范式',
      vision_title: '我们的愿景',
      vision_desc: 'LingNow 源于一个简单而深邃的想法：我们能否将软件开发中最具创造力的部分自动化？我们的 AI 研究与开发团队致力于构建一个由专业辅助智能体在秒级完成复杂架构规划、视觉设计与安全审计的世界。',
      stat_fidelity: '高保真还原目标',
      stat_agents: '专业智能体岗位',
      factory_title: '工厂化模型',
      factory_desc: '我们不只是提供工具，我们提供的是一座自动化工厂。LingNow 的多智能体网络模拟人类协作流程，确保在追求极速交付的同时，设计质量、安全性和系统性能永不妥协。',
      cta_docs: '阅读文档',
      cta_home: '返回首页'
    }
  }
}

const t = (path) => {
  const keys = path.split('.')
  let res = translations[currentLang.value]
  for (const key of keys) {
    res = res ? res[key] : null
  }
  return res || path
}

onMounted(() => {
  const saved = localStorage.getItem('lang')
  if (saved) currentLang.value = saved
})
</script>
