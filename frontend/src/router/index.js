import {createRouter, createWebHistory} from 'vue-router'
import Workbench from '../views/Workbench.vue'

const routes = [
    {
        path: '/',
        name: 'Home',
        component: Workbench
    },
    {
        path: '/project/:id',
        name: 'Project',
        component: Workbench,
        props: true
    }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

export default router
