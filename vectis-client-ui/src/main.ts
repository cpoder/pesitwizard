/**
 * main.ts
 *
 * Bootstraps Vuetify and other plugins then mounts the App`
 */

// Composables
import { createPinia } from 'pinia'
import { createApp } from 'vue'

// Components
import App from './App.vue'

// Router
import router from './router'

// Styles
import './assets/main.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)

app.mount('#app')
