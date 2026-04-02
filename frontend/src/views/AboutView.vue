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
          class="inline-flex items-center gap-2 px-3 py-1 rounded-full border border-white/10 bg-white/5 text-[10px] font-bold tracking-widest text-cyan-400 uppercase mb-8">
        The Mission
      </div>
      <h1 class="text-7xl font-black mb-12 tracking-tighter leading-none italic">
        Revolutionizing <br/>
        <span
            class="text-transparent bg-clip-text bg-gradient-to-r from-cyan-400 to-purple-600">Software Production</span>
      </h1>

      <div class="glass-morphism rounded-[2.5rem] p-12 border border-white/10 space-y-12">
        <section>
          <h2 class="text-2xl font-bold mb-6 text-cyan-400 tracking-tight">Our Vision</h2>
          <p class="text-xl text-gray-400 leading-relaxed font-light">
            LingNow was born from a simple yet radical idea: What if we could automate the most creative parts of
            software development? Our team of AI researchers and developers is building a world where complex
            architectures are architected, designed, and audited by specialized AI agents in seconds.
          </p>
        </section>

        <div class="grid grid-cols-1 md:grid-cols-2 gap-8 py-8 border-y border-white/5">
          <div class="p-6 rounded-3xl bg-white/5 border border-white/5">
            <div class="text-3xl font-black mb-2">99.9%</div>
            <div class="text-[10px] text-gray-500 uppercase font-black tracking-widest">UI Fidelity Goal</div>
          </div>
          <div class="p-6 rounded-3xl bg-white/5 border border-white/5">
            <div class="text-3xl font-black mb-2">4+</div>
            <div class="text-[10px] text-gray-500 uppercase font-black tracking-widest">Specialized Agent Roles</div>
          </div>
        </div>

        <section>
          <h2 class="text-2xl font-bold mb-6 text-purple-400 tracking-tight">The Factory Model</h2>
          <p class="text-lg text-gray-500 leading-relaxed">
            We don't provide a tool; we provide a factory. LingNow's multi-agent network mimics human teamwork, ensuring
            that design, security, and performance are never compromised for speed. Join us on our journey to
            industrialize the digital future.
          </p>
        </section>
      </div>

      <div class="mt-20 flex justify-center gap-8">
        <button class="text-gray-500 hover:text-white transition-colors text-sm font-bold uppercase tracking-widest"
                @click="$router.push('/docs')">
          Read documentation
        </button>
        <button class="text-gray-500 hover:text-white transition-colors text-sm font-bold uppercase tracking-widest"
                @click="$router.push('/')">
          Back to home
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import {ref} from 'vue'

const currentLang = ref('cn')
</script>
