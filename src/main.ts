import Vue from 'vue'
import App from './App.vue'
import router from './router'
import i18n from './translations'
import Vuex from 'vuex'
import store from './states'
import Toasted from 'vue-toasted'

Vue.config.productionTip = false
Vue.use(Toasted, {
	router
})
/* eslint-disable no-new */

new Vue({
	el: '#app',
	store,
	router,
	i18n,
	template: '<App/>',
	data: {},
	components: {
		App
	}
})