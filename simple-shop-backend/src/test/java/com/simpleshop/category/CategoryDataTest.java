package com.simpleshop.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.simpleshop.category.entity.CategoryL1;
import com.simpleshop.category.entity.CategoryL2;
import com.simpleshop.testing.DataLayerTestBase;

class CategoryDataTest extends DataLayerTestBase {

    @Test
    @DisplayName("同一父级下二级类别名唯一")
    void categoryL2NameIsUniqueUnderParent() {
        CategoryL2 first = createCategory();
        CategoryL2 duplicate = CategoryL2.create(first.getL1(), first.getName());

        assertThatThrownBy(() -> categoryL2s.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("不同一级类别下可以有同名二级类别")
    void sameL2NameAllowedUnderDifferentParents() {
        CategoryL2 first = createCategory();
        CategoryL1 otherL1 = categoryL1s.saveAndFlush(CategoryL1.create(unique("L1-")));
        CategoryL2 other = CategoryL2.create(otherL1, first.getName());

        categoryL2s.saveAndFlush(other);

        assertThat(categoryL2s.findByL1_IdAndName(otherL1.getId(), first.getName()))
                .get()
                .extracting(CategoryL2::getId)
                .isEqualTo(other.getId());
    }

    @Test
    @DisplayName("一级类别仍有二级子类时不能删除")
    void categoryL1WithChildrenCannotBeDeleted() {
        CategoryL2 child = createCategory();
        String l1Id = child.getL1().getId();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "delete from simpleshop_category_l1 where id = ?", l1Id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
