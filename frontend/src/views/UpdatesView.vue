<template>
  <div class="min-h-screen tech-grid text-white selection:bg-cyan-500/30 overflow-x-hidden">
    <!-- Navbar (Consistent) -->
    <nav
        class="fixed top-0 w-full z-50 px-8 py-6 flex justify-between items-center glass-morphism border-b border-white/5">
      <div class="flex items-center gap-2 cursor-pointer" @click="$router.push('/')">
        <div
            class="w-8 h-8 bg-gradient-to-br from-cyan-400 to-purple-600 rounded-lg flex items-center justify-center font-bold text-xl text-white">
          L
        </div>
        <span class="text-xl font-bold tracking-tight text-white">LingNow</span>
      </div>

      <div class="hidden md:flex gap-8 text-sm font-medium text-gray-400">
        <router-link class="hover:text-white transition-colors" to="/">{{
            currentLang === 'cn' ? '平台' : 'Platform'
          }}
        </router-link>
        <router-link class="hover:text-white transition-colors" to="/solutions">
          {{ currentLang === 'cn' ? '解决方案' : 'Solutions' }}
        </router-link>
        <router-link class="hover:text-white transition-colors" to="/#pricing">
          {{ currentLang === 'cn' ? '定价' : 'Pricing' }}
        </router-link>
      </div>

      <div class="flex gap-4 items-center">
        <div class="hidden lg:flex gap-6 text-xs font-bold text-gray-500 tracking-widest mr-4">
          <router-link class="hover:text-cyan-400 transition-colors" to="/docs">
            {{ currentLang === 'cn' ? '文档' : 'Docs' }}
          </router-link>
          <router-link class="hover:text-cyan-400 transition-colors" to="/updates">
            {{ currentLang === 'cn' ? '动态' : 'Updates' }}
          </router-link>
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
            <button class="w-full text-left px-3 py-2 rounded-lg hover:bg-white/5 text-xs" @click="currentLang = 'cn'">
              🇨🇳 中文
            </button>
            <button class="w-full text-left px-3 py-2 rounded-lg hover:bg-white/5 text-xs" @click="currentLang = 'en'">
              🇺🇸 English
            </button>
          </div>
        </div>
        <button class="px-5 py-2 bg-white text-black rounded-full text-sm font-bold hover:bg-cyan-400 transition-all"
                @click="$router.push('/workbench')">
          Launch
        </button>
      </div>
    </nav>

    <div class="max-w-4xl mx-auto pt-48 pb-32 px-8">
      <div
          class="inline-flex items-center gap-2 px-3 py-1 rounded-full border border-white/10 bg-white/5 text-[10px] font-bold tracking-widest text-[#9D00FF] uppercase mb-8">
        Evolution Log
      </div>
      <h1 class="text-5xl font-black mb-16 tracking-tighter leading-tight">
        Agent Network <br/>
        <span class="text-gray-500 italic">Weekly Iterations</span>
      </h1>

      <div class="space-y-16 relative">
        <!-- Vertical Timeline Line -->
        <div class="absolute left-6 top-0 bottom-0 w-px bg-white/5"></div>

        <div v-for="log in logs" :key="log.date" class="relative pl-20 group">
          <!-- Timeline Node -->
          <div
              class="absolute left-[20px] top-4 w-2.5 h-2.5 rounded-full bg-cyan-500 shadow-[0_0_10px_rgba(0,209,255,0.8)] border-2 border-[#050505] group-hover:scale-125 transition-transform z-10"></div>

          <div class="text-[10px] font-black text-gray-600 mb-2 uppercase tracking-[0.2em]">{{ log.date }}</div>
          <div class="glass-morphism rounded-3xl p-8 border border-white/10 group-hover:border-white/20 transition-all">
            <h3 class="text-2xl font-bold mb-4 tracking-tight group-hover:text-cyan-400 transition-colors">{{
                log.title
              }}</h3>
            <p class="text-gray-400 leading-relaxed text-sm mb-6">{{ log.description }}</p>

            <div class="flex flex-wrap gap-2">
              <span v-for="tag in log.tags" :key="tag"
                    class="px-3 py-1 rounded-full bg-white/5 border border-white/5 text-[8px] font-black text-gray-500 uppercase tracking-widest">{{
                  tag
                }}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="mt-32 text-center">
        <p class="text-gray-600 text-xs font-bold uppercase tracking-widest mb-4 italic">End of timeline for this
          month</p>
        <button class="px-6 py-2 rounded-full border border-white/10 hover:bg-white/5 transition-colors text-[10px] font-black uppercase tracking-widest"
                @click="$router.push('/')">
          Back to hub
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>

const logs = [
  {
    date: 'April 2026, Week 2',
    title: 'Industrial Snap v8.0 Deployment',
    description: 'We refined the multi-page portal architecture and implemented a new vertical wrapping grid for industry showcase. The agent audit network now supports two-phase commits for code safety.',
    tags: ['Core Infrastructure', 'Aesthetics', 'Routing']
  },
  {
    date: 'April 2026, Week 1',
    title: 'Expert Logic Migration',
    description: 'Migrating hardcoded prompt strategies to a centralized Agent Constitution. Improved UI accuracy by 32% for healthcare and SaaS fintech modules.',
    tags: ['Agents', 'Intelligence']
  },
  {
    date: 'March 2026, Week 4',
    title: 'Multilingual Foundation Fix',
    description: 'Seamless integration of CN/EN flag-based switching. Optimized the internationalization pipeline for production grade portals.',
    tags: ['i18n', 'Globalization']
  }
]

</script>
