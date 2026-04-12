<template>
  <Transition name="fade-scale">
    <div v-if="store.isPricingOpen"
         class="fixed inset-0 z-[200] flex items-center justify-center p-4 md:p-8 bg-white/5 backdrop-blur-md overflow-y-auto"
         @click.self="store.isPricingOpen = false">

      <div
          class="relative w-full max-w-7xl bg-white/[0.01] backdrop-blur-3xl rounded-[3rem] border border-white/40 shadow-[0_0_100px_rgba(255,255,255,0.05)] p-8 md:p-16 flex flex-col items-center">
        <!-- Close Button -->
        <button
            class="absolute top-8 right-8 w-12 h-12 rounded-xl bg-white/5 border border-white/10 flex items-center justify-center hover:bg-white/10 transition-all text-xl"
            @click="store.isPricingOpen = false">✕
        </button>

        <!-- Header: Toggle -->
        <div class="text-center mb-16">
          <h2 class="text-4xl font-black mb-6 tracking-tighter">{{ t('pricing.modal_title') }}</h2>
          <div class="inline-flex p-1 bg-white/5 rounded-2xl border border-white/10">
            <button v-for="mode in ['month', 'year']" :key="mode"
                    :class="priceMode === mode ? 'bg-white text-black' : 'text-gray-400 hover:text-white'"
                    class="px-8 py-2.5 rounded-xl text-sm font-bold transition-all flex items-center gap-2"
                    @click="priceMode = mode">
              {{ t(`pricing.toggle.${mode}`) }}
              <span v-if="mode === 'year'"
                    class="text-[10px] px-1.5 py-0.5 bg-cyan-500/20 text-cyan-400 rounded-md">7折</span>
            </button>
          </div>
        </div>

        <!-- Pricing Grid -->
        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 w-full">
          <div v-for="plan in plans" :key="plan.id"
               :class="{'border-cyan-500/50 shadow-[0_0_40px_rgba(34,211,238,0.1)]': plan.id === 'advanced'}"
               class="group relative glass-morphism rounded-[2.5rem] p-8 border border-white/10 flex flex-col transition-all hover:scale-[1.02] hover:border-white/30">

            <div v-if="plan.id === 'advanced'"
                 class="absolute -top-3 left-1/2 -translate-x-1/2 px-4 py-1 bg-gradient-to-r from-orange-400 to-pink-500 text-black text-[10px] font-black rounded-full tracking-widest uppercase">
              🔥 {{ t('pricing.badges.popular') }}
            </div>

            <div class="mb-8">
              <h3 class="text-xl font-bold mb-4 tracking-tight">{{ t(`pricing.plans.${plan.id}.name`) }}</h3>
              <div class="flex items-baseline gap-1">
                <span class="text-3xl font-black">¥{{ priceMode === 'year' ? plan.priceYear : plan.priceMonth }}</span>
                <span class="text-xs text-gray-500">/{{
                    priceMode === 'year' ? t('pricing.unit.year') : t('pricing.unit.month')
                  }}</span>
              </div>
            </div>

            <button :class="plan.id === 'free' ? 'bg-white/5 hover:bg-white/10 text-white' : 'bg-white text-black hover:bg-cyan-400'"
                    class="w-full py-4 rounded-2xl font-black text-sm mb-10 transition-all border border-white/10">
              {{ plan.id === 'free' ? t('pricing.cta.free') : t('pricing.cta.buy') }}
            </button>

            <ul class="space-y-4 mb-8 flex-1">
              <li v-for="(feature, idx) in t(`pricing.plans.${plan.id}.features`)" :key="idx"
                  class="flex gap-3 text-xs leading-relaxed text-gray-400">
                <div class="w-4 h-4 rounded-full bg-cyan-500/10 flex items-center justify-center shrink-0 mt-0.5">
                  <svg class="text-cyan-400" fill="none" height="10" stroke="currentColor" stroke-linecap="round"
                       stroke-linejoin="round" stroke-width="3" viewBox="0 0 24 24" width="10"
                       xmlns="http://www.w3.org/2000/svg">
                    <polyline points="20 6 9 17 4 12"/>
                  </svg>
                </div>
                <span>{{ feature }}</span>
              </li>
            </ul>
          </div>
        </div>

        <p class="mt-12 text-[10px] text-gray-600 font-bold uppercase tracking-widest text-center">
          {{ t('pricing.footer_hint') }} <a class="text-cyan-500 hover:underline" href="#">用户须知</a> & <a class="text-cyan-500 hover:underline"
                                                                                                             href="#">隐私条款</a>
        </p>
      </div>
    </div>
  </Transition>
</template>

<script setup>
import {ref} from 'vue'
import {store} from '../store'

const currentLang = ref(localStorage.getItem('lang') || 'cn')
const priceMode = ref('month') // 'month' or 'year'

const plans = [
  {id: 'free', priceMonth: 0, priceYear: 0},
  {id: 'standard', priceMonth: 149, priceYear: 1250},
  {id: 'advanced', priceMonth: 259, priceYear: 2170},
  {id: 'premium', priceMonth: 1299, priceYear: 10900}
]

const translations = {
  en: {
    pricing: {
      modal_title: 'Global Delivery Plans',
      toggle: {month: 'Monthly', year: 'Yearly'},
      unit: {month: 'mo', year: 'yr'},
      badges: {popular: 'Most Popular'},
      cta: {free: 'FREE START', buy: 'PURCHASE'},
      footer_hint: 'By continuing, you agree to our',
      plans: {
        free: {
          name: 'Free',
          features: ['2 Active Project Slot', 'Generation: 0 count/mo', 'Code Preview Only', 'Manual Deployment']
        },
        standard: {
          name: 'Standard',
          features: ['5 Active Project Slots', 'Generation: 40 count/mo', 'Full Code Export', 'Basic Agent Support']
        },
        advanced: {
          name: 'Advanced',
          features: ['15 Active Project Slots', 'Generation: 100 count/mo', 'Industrial Deployment', 'Prioritized Agent Queues', 'Custom Agent Skills']
        },
        premium: {
          name: 'Premium',
          features: ['Unlimited Projects', 'Generation: 500 count/mo', 'Private Cloud Hosting', '24/7 Agent Monitoring', 'SLA Guarantee']
        }
      }
    }
  },
  cn: {
    pricing: {
      modal_title: '全球工业交付计划',
      toggle: {month: '单月', year: '单年'},
      unit: {month: '月', year: '年'},
      badges: {popular: '进阶推荐'},
      cta: {free: '免费使用', buy: '立即购买'},
      footer_hint: '继续操作即表示您同意我们的',
      plans: {
        free: {name: '免费', features: ['2 个活跃项目名额', '每月生成次数: 0', '仅限代码预览', '手动本地部署']},
        standard: {
          name: '标准',
          features: ['5 个活跃项目名额', '每月生成次数: 40', '完整源代码导出', '基础版智能体矩阵']
        },
        advanced: {
          name: '进阶',
          features: ['15 个活跃项目名额', '每月生成次数: 100', '工业级云端部署', '优先排队智能体队列', '自定义 Agent 技能']
        },
        premium: {
          name: '尊享',
          features: ['不限项目名额', '每月生成次数: 500', '私有云物理隔离', '24/7 全天候专家监控', 'SLA 服务保障协议']
        }
      }
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
</script>

<style scoped>
.fade-scale-enter-active, .fade-scale-leave-active {
  transition: all 0.4s cubic-bezier(0.16, 1, 0.3, 1);
}

.fade-scale-enter-from, .fade-scale-leave-to {
  opacity: 0;
  transform: scale(0.95);
}

.custom-scrollbar::-webkit-scrollbar {
  width: 4px;
}

.custom-scrollbar::-webkit-scrollbar-track {
  background: transparent;
}

.custom-scrollbar::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.1);
  border-radius: 10px;
}
</style>
