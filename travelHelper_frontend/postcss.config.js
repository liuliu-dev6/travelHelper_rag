// postcss.config.js
export default {
  plugins: {
    'postcss-pxtorem': {
      rootValue: 37.5, // 设计稿宽度/10，常用设计稿为375px，所以这里是37.5
      propList: ['*'], // 所有属性都转换
      selectorBlackList: ['.norem'] // 保留不想转换的选择器，比如 .norem 类名
    }
  }
}
