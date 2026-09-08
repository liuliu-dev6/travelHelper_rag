import { createApp, h } from 'vue'
import MyToast from '../components/MyToast.vue'

type ToastType = 'success' | 'fail' | 'loading' | 'text'

interface ToastOptions {
  message: string
  type?: ToastType
  duration?: number
}

const toast = {
  show(options: ToastOptions | string) {
    const props = typeof options === 'string' 
      ? { message: options, type: 'text' as ToastType }
      : { ...options }

    const vNode = h(MyToast, {
      visible: true,
      message: props.message,
      type: props.type || 'text',
      duration: props.duration || 2000,
      'onUpdate:visible': (val: boolean) => {
        if (!val) {
          app.unmount()
          container.remove()
        }
      }
    })

    const container = document.createElement('div')
    document.body.appendChild(container)
    
    const app = createApp(vNode)
    app.mount(container)
  },

  success(message: string, duration?: number) {
    this.show({ message, type: 'success', duration })
  },

  fail(message: string, duration?: number) {
    this.show({ message, type: 'fail', duration })
  },

  loading(message: string = '加载中...') {
    this.show({ message, type: 'loading', duration: 0 })
  },

  text(message: string, duration?: number) {
    this.show({ message, type: 'text', duration })
  }
}

export default toast
