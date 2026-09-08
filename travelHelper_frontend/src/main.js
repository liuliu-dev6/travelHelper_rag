import 'amfe-flexible'
import { createApp } from 'vue'

import 'vant/lib/index.css'
import './styles/vant-overrides.css'
import App from './App.vue'
import pinia from './stores'
import router from './router'
import { Button, Card, Tabbar, TabbarItem, NavBar, NoticeBar, Loading, Empty, Collapse, CollapseItem, Icon, Field, Popup, Picker, Grid, GridItem, Cell, CellGroup } from 'vant'

const app = createApp(App)
app.use(pinia)
app.use(router)
app.use(Button)
app.use(Card)
app.use(Tabbar)
app.use(TabbarItem)
app.use(NavBar)
app.use(NoticeBar)
app.use(Loading)
app.use(Empty)
app.use(Collapse)
app.use(CollapseItem)
app.use(Icon)
app.use(Field)
app.use(Popup)
app.use(Picker)
app.use(Grid)
app.use(GridItem)
app.use(Cell)
app.use(CellGroup)

app.mount('#app')
