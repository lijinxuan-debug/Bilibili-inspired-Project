// 补丁：getAllCategoryInfo 在 Redis 为空时写入后应重新读取，否则客户端拿到 data:[] 无法回填分区名称。
@Override
public List<CategoryInfo> getAllCategoryInfo() {
    List<CategoryInfo> categoryInfoList = redisComponent.getCategoryInfo();
    if (categoryInfoList == null || categoryInfoList.isEmpty()) {
        saveCategoryInfo2Redis();
        categoryInfoList = redisComponent.getCategoryInfo();
    }
    return categoryInfoList == null ? java.util.Collections.emptyList() : categoryInfoList;
}
