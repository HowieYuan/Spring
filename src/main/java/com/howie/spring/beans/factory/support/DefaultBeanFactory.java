package com.howie.spring.beans.factory.support;

import com.howie.spring.beans.BeanDefinition;
import com.howie.spring.beans.PropertyValue;
import com.howie.spring.beans.SimpleTypeConverter;
import com.howie.spring.beans.exception.BeanCreationException;
import com.howie.spring.beans.factory.config.ConfigurableBeanFactory;
import com.howie.spring.util.ClassUtils;
import com.howie.spring.beans.factory.BeanFactory;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created with IntelliJ IDEA
 *
 * @Author yuanhaoyue swithaoy@gmail.com
 * @Description 处理bean和BeanDefinition的工厂
 * @Date 2018-10-04
 * @Time 17:30
 */
public class DefaultBeanFactory extends DefaultSingletonBeanRegistry
        implements ConfigurableBeanFactory, BeanDefinitionRegistry {
    /**
     * xml 文件中各个 <bean>
     */
    private final Map<String, BeanDefinition> beanDefinitionMap = new ConcurrentHashMap<String, BeanDefinition>(64);

    private ClassLoader classLoader = null;

    /**
     * 获得某个 bean 的 BeanDefinition
     *
     * @param beanID bean id
     */
    @Override
    public BeanDefinition getBeanDefinition(String beanID) {
        return this.beanDefinitionMap.get(beanID);
    }

    @Override
    public void registryBeanDefinition(String id, BeanDefinition bd) {
        this.beanDefinitionMap.put(id, bd);
    }

    /**
     * 获得某个 bean 对象
     *
     * @param beanID bean id
     */
    @Override
    public Object getBean(String beanID) {
        //GenericBeanDefinition
        BeanDefinition bd = this.getBeanDefinition(beanID);
        if (bd == null) {
            throw new BeanCreationException("Bean Definition does not exist");
        }
        if (bd.isSingleton()) {
            Object bean = this.getSingletonBean(beanID);
            if (bean != null) {
                return bean;
            }
            bean = this.createBean(bd);
            this.registerSingleton(beanID, bean);
            return bean;
        }
        return this.createBean(bd);
    }

    /**
     * 创建bean
     */
    private Object createBean(BeanDefinition bd) {
        Object bean = instanceBean(bd);
        populateBean(bd, bean);
        return bean;
    }

    /**
     * 填充bean的属性,执行setter方法
     */
    private void populateBean(BeanDefinition bd, Object bean) {
        //获得该bean所有的property
        List<PropertyValue> propertyValueList = bd.getPropertyValues();
        if (propertyValueList == null || propertyValueList.isEmpty()) {
            return;
        }
        BeanDefinitionValueResolver resolver = new BeanDefinitionValueResolver(this);
        SimpleTypeConverter converter = new SimpleTypeConverter();
        //遍历，一个个执行setter方法
        for (PropertyValue propertyValue : propertyValueList) {
            String name = propertyValue.getName();
            Object value = propertyValue.getValue();
            //如果是ref属性，则获取实例
            value = resolver.resolveValueIfNecessary(value);
            try {
                //利用BeanInfo进行setter注入
                BeanInfo beanInfo = Introspector.getBeanInfo(bean.getClass());
                PropertyDescriptor[] descriptors = beanInfo.getPropertyDescriptors();
                for (PropertyDescriptor descriptor : descriptors) {
                    if (name.equals(descriptor.getName())) {
                        //进行类型转换（如String转为int）
                        Object convertedValue = converter.convertIfNecessary(value,
                                descriptor.getPropertyType());
                        //通过反射执行setter方法
                        descriptor.getWriteMethod().invoke(bean, convertedValue);
                        break;
                    }
                }
            } catch (IntrospectionException | IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 获得bean的实例
     */
    private Object instanceBean(BeanDefinition bd) {
        //如果拥有构造器参数，则直接使用autowireConstructor方法获得实例
        if (bd.hasConstructorArgumentValues()) {
            ConstructorResolver resolver = new ConstructorResolver(this);
            return resolver.autowireConstructor(bd);
        } else {
            ClassLoader cl = this.getBeanClassLoader();
            String beanClassName = bd.getBeanClassName();
            try {
                Class<?> clz = cl.loadClass(beanClassName);
                return clz.newInstance();
            } catch (Exception e) {
                throw new BeanCreationException("create bean for " + beanClassName + " failed", e);
            }
        }
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public ClassLoader getBeanClassLoader() {
        return this.classLoader == null ? ClassUtils.getDefaultClassLoader() : classLoader;
    }
}
