# exposure 适配包

exposure:camera在客户端owner以DEBUG Provider读取真实手持相机/胶卷：活动状态、是否装片、已有照片数量和有界标识。使用只读附件访问，不创建胶卷ID、不调用快门或附件setter。独服不执行客户端入口。

## 验证

具名场景明确准备相机和胶卷，随后通过真实附件界面Slot装片、正常输入拍摄两次，独立确认两个不同照片标识。界面和快门需TAKEOVER/Lease；Provider读取需独立DEBUG读取授权。此范围不宣称冲洗、打印、相册或全部Exposure UI已经验收。
