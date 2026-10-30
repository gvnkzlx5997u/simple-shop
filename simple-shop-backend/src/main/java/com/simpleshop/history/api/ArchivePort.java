package com.simpleshop.history.api;

import java.util.List;

/**
 * 归档写入端口：只接收快照命令，要求调用方已开启事务。
 */
public interface ArchivePort {

    void archive(GoodsArchiveCommand goods, List<IntentionArchiveCommand> intentions);
}
