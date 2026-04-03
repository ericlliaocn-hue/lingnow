<template>
  <div class="min-h-screen text-white selection:bg-cyan-500/30 overflow-x-hidden">
    <!-- Navbar (Consistent) -->
    <nav
        class="fixed top-0 w-full z-50 px-8 py-6 flex justify-between items-center glass-morphism">
      <div class="flex items-center gap-2 cursor-pointer" @click="$router.push('/')">
        <svg class="w-9 h-9 text-white" fill="none" stroke="currentColor" viewBox="0 0 100 100"
             xmlns="http://www.w3.org/2000/svg">
          <g stroke-linejoin="miter" stroke-width="2">
            <polyline opacity="0.3" points="20,60 20,20 60,60 60,20"/>
            <polyline opacity="0.6" points="30,70 30,30 70,70 70,30"/>
            <polyline opacity="1.0" points="40,80 40,40 80,80 80,40"/>
            <line opacity="0.3" x1="20" x2="40" y1="20" y2="40"/>
            <line opacity="0.3" x1="60" x2="80" y1="20" y2="40"/>
            <line opacity="0.6" x1="20" x2="40" y1="60" y2="80"/>
            <line opacity="0.6" x1="60" x2="80" y1="60" y2="80"/>
          </g>
        </svg>
        <span class="text-xl font-bold tracking-tight text-white">LingNow</span>
      </div>

      <div class="hidden md:flex gap-8 text-sm font-medium text-gray-400">
        <router-link class="hover:text-white transition-colors" to="/solutions">{{
            currentLang === 'cn' ? '解决方案' : 'Solutions'
          }}
        </router-link>
        <a class="hover:text-white transition-colors cursor-pointer" @click="store.isPricingOpen = true">{{
            currentLang === 'cn' ? '定价' : 'Pricing'
          }}</a>
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
        <!-- i18n Dropdown (Minimal version for subpages) -->
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

    <div class="max-w-7xl mx-auto pt-32 flex flex-col md:flex-row gap-12 px-8">
      <!-- Sidebar Navigation -->
      <aside
          class="w-full md:w-64 flex-none sticky top-32 h-auto md:h-[calc(100vh-160px)] overflow-y-auto custom-scrollbar">
        <div v-for="group in docGroups" :key="group.title" class="mb-10">
          <h4 class="text-[10px] font-black text-gray-500 uppercase tracking-widest mb-4">{{ tDoc(group.title) }}</h4>
          <ul class="space-y-2">
            <li v-for="item in group.items" :key="item.id"
                :class="activeTopic === item.id ? 'bg-cyan-500/10 border-cyan-500/30 text-cyan-400' : 'text-gray-400 hover:bg-white/5 hover:text-white'"
                class="px-4 py-2 rounded-xl text-sm cursor-pointer transition-all border border-transparent"
                @click="activeTopic = item.id">
              {{ tDoc(item.label) }}
            </li>
          </ul>
        </div>
      </aside>

      <!-- Main Content Area -->
      <main class="flex-1 pb-32">
        <Transition mode="out-in" name="fade">
          <div :key="activeTopic" class="glass-morphism rounded-[2.5rem] p-12 border border-white/10">
            <div class="flex items-center gap-2 text-xs text-gray-500 mb-6 uppercase tracking-widest font-bold">
              <span>{{ currentLang === 'cn' ? '文档' : 'Docs' }}</span>
              <span>/</span>
              <span class="text-cyan-500">{{ activeTopic }}</span>
            </div>

            <h1 class="text-5xl font-black mb-8 tracking-tighter">{{ tContent(activeTopic, 'title') }}</h1>
            <p class="text-lg text-gray-400 leading-relaxed mb-12 border-l-2 border-cyan-500/30 pl-6 italic">
              {{ tContent(activeTopic, 'lead') }}
            </p>

            <div class="prose prose-invert max-w-none space-y-8 text-gray-300 leading-loose">
              <div v-for="(block, idx) in tContent(activeTopic, 'blocks')" :key="idx">
                <h3 class="text-2xl font-bold text-white mb-4">{{ block.heading }}</h3>
                <p>{{ block.text }}</p>
                <div v-if="block.code"
                     class="bg-black/50 rounded-2xl p-6 border border-white/5 font-mono text-xs mt-4 relative group">
                  <pre><code>{{ block.code }}</code></pre>
                </div>
              </div>
            </div>

            <!-- Interaction Cta -->
            <div
                class="mt-20 p-10 rounded-[2rem] bg-gradient-to-br from-cyan-500/10 to-purple-600/10 border border-white/5 flex flex-col md:flex-row items-center justify-between gap-6">
              <div>
                <h4 class="text-xl font-bold mb-2">{{
                    currentLang === 'cn' ? '准备好开始构建了吗？' : 'Ready to Build?'
                  }}</h4>
                <p class="text-gray-400 text-sm">{{
                    currentLang === 'cn' ? '现在部署您的第一个工业级智能体。' : 'Deploy your first industrial-grade agent now.'
                  }}</p>
              </div>
              <button class="px-8 py-3 bg-white text-black font-black rounded-2xl hover:scale-105 transition-transform flex-none"
                      @click="$router.push('/workbench')">
                {{ currentLang === 'cn' ? '启动工作台' : 'Launch Workbench' }}
              </button>
            </div>
          </div>
        </Transition>
      </main>
    </div>
  </div>
</template>

<script setup>
import {ref} from 'vue'

const currentLang = ref(localStorage.getItem('lang') || 'cn')
const activeTopic = ref('getting-started')

const setLang = (lang) => {
  currentLang.value = lang
  localStorage.setItem('lang', lang)
}

const docGroups = [
  {
    title: 'Introduction',
    items: [
      {id: 'getting-started', label: 'Getting Started'},
      {id: 'agent-network', label: 'Agent Network'}
    ]
  },
  {
    title: 'Standards',
    items: [
      {id: 'security', label: 'Security Protocols'}
    ]
  }
]

const translations = {
  cn: {
    Introduction: '基础入门',
    'Getting Started': '快速起步',
    'Agent Network': '智能体联结架构',
    Standards: '工业标准',
    'Security Protocols': '安全审计体系',
    'getting-started': {
      title: '从创意到工业级原型',
      lead: 'LingNow 不仅仅是一个 AI 聊天机器人。它是一个能够理解架构、审美评分和安全逻辑的多智能体协作工厂。',
      blocks: [
        {
          heading: '核心逻辑',
          text: '每个成功的项目都源于一个精准的提示词。我们的专家 Agent 会将其拆解为规划员、设计师、工程师和审计员四个关键岗位协同工作。'
        },
        {
          heading: '快速部署',
          text: '无需环境配置，Agent 矩阵将自洽地处理所有依赖与路由逻辑。',
          code: 'npm install @lingnow/agent-core\nlingnow init local_factory_v1'
        }
      ]
    },
    'security': {
      title: '安全与完整性建议',
      lead: '每个生成的组件都必须通过 Agent 矩阵的 42 项安全性检查，确保原型即生产可用。',
      blocks: [
        {
          heading: '两阶段验证',
          text: '第一阶段负责逻辑闭环，第二阶段负责样式抛光与边界情况审计。',
          code: '// auditor.verify(project, { rule: "industrial" });'
        }
      ]
    }
  }
}

const tDoc = (key) => translations[currentLang.value]?.[key] || key
const tContent = (topicId, type) => translations[currentLang.value]?.[topicId]?.[type] || []

</script>

<style scoped>
.custom-scrollbar::-webkit-scrollbar {
  width: 4px;
}

.custom-scrollbar::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.05);
  border-radius: 10px;
}

.custom-scrollbar::-webkit-scrollbar-thumb:hover {
  background: rgba(0, 209, 255, 0.2);
}

.fade-enter-active, .fade-leave-active {
  transition: opacity 0.3s ease, transform 0.3s ease;
}

.fade-enter-from, .fade-leave-to {
  opacity: 0;
  transform: translateY(10px);
}
</style>
