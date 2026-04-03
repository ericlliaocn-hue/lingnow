import {reactive} from 'vue'

export const store = reactive({
    isPricingOpen: false,
    isLoading: false,
    togglePricing() {
        this.isPricingOpen = !this.isPricingOpen
    }
})
