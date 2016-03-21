package com.sample;

import javax.inject.Inject;
import java.lang.reflect.*;
import java.util.*;

/**
 * Created by yotamm on 20/02/16.
 */
public class SimpleApplicationContext implements ApplicationContext{

    public enum BeanResolutionState {RESOLVED, UNRESOLVED, IN_PROGRESS}
    public static class CyclicDependencyException extends RuntimeException{
        public CyclicDependencyException(String msg){super(msg);}
    }

    public static class AmbigiousBeanDefException extends RuntimeException{
        public AmbigiousBeanDefException(String msg){super(msg);}
    }

    public static class UndefinedBeanException extends RuntimeException{
        public UndefinedBeanException(String msg){super(msg);}
    }

    public static class MissingSuitableConstructorException extends RuntimeException{
        public MissingSuitableConstructorException(String msg){super(msg);}
    }

    public static class BeanInstatiationException extends RuntimeException{
        public BeanInstatiationException(Throwable throwable){super(throwable);}
    }

    class BeanDefinition{
        Class beanType = null;
        Object instance = null;
        Class factoryBean = null;
        BeanResolutionState beanResolutionState = BeanResolutionState.UNRESOLVED;
        public String getName() {
            return beanType.getName();
        }
    }

    private Set<String> unresolvedBeans = new HashSet<String>();
    private Map<String, BeanDefinition> beans = new HashMap<String, BeanDefinition>();
    private Map<String, List<BeanDefinition>> beansByType = new HashMap<String, List<BeanDefinition>>();

    public SimpleApplicationContext(Class[] classes, Object[] singletones){
        populateUnresolvedBeans(classes, singletones);
        populateBeansByType();
        resolveBeans();
    }

    private void populateBeansByType() {
        for (BeanDefinition beanDefinition : beans.values().toArray(new BeanDefinition[]{})){
            Class[] allTypes = getAllTypes(beanDefinition);
            for (Class type : allTypes){
                List<BeanDefinition> beansForType = beansByType.get(type.getName());
                if (beansForType == null){
                    beansForType = new ArrayList<BeanDefinition>();
                    beansByType.put(type.getName(), beansForType);
                }
                beansForType.add(beanDefinition);
            }
        }

    }

    private Class[] getAllTypes(BeanDefinition beanDefinition) {
        List<Class> types = new ArrayList<Class>();
        getAllTypes(types, beanDefinition.beanType);
        return types.toArray(new Class[]{});
    }

    private void getAllTypes(List<Class> types, Class beanType) {
        types.add(beanType);
        for (Class interf : beanType.getInterfaces()){
            types.add(interf);
        }
        if (beanType.getSuperclass() != null && beanType.getSuperclass() != Object.class){
            getAllTypes(types, beanType.getSuperclass());
        }
    }

    private void populateUnresolvedBeans(Class[] classes, Object[] singletones) {
        for (Class beanClass : classes){
            BeanDefinition definition = new BeanDefinition();
            definition.beanType = beanClass;
            definition.instance = null;
            addUnresolvedBean(definition);
        }
        for (Object beanInstance : singletones){
            BeanDefinition definition = new BeanDefinition();
            definition.instance = beanInstance;
            definition.beanType = beanInstance.getClass();
            addUnresolvedBean(definition);
        }
        for (BeanDefinition beanDefinition : beans.values().toArray(new BeanDefinition[]{})){
            if (BeanFactory.class.isAssignableFrom(beanDefinition.beanType)){
                addFactoryProductBeanDef(beanDefinition);
            }
        }
    }

    private void addUnresolvedBean(BeanDefinition definition) {
        definition.beanResolutionState = BeanResolutionState.UNRESOLVED;
        unresolvedBeans.add(definition.getName());
        beans.put(definition.getName(), definition);
    }

    private void addFactoryProductBeanDef(BeanDefinition factoryBeanDef) {
        Type[] types = getGenericTypes(factoryBeanDef.beanType, BeanFactory.class);
        Class productType = (Class) types[0];
        BeanDefinition productDefinition = new BeanDefinition();
        productDefinition.beanType = productType;
        productDefinition.factoryBean = factoryBeanDef.beanType;
        productDefinition.instance = null;
        addUnresolvedBean(productDefinition);
    }

    private Type[] getGenericTypes(Class clazz, Class interfaceClazz) {
        Type[] genericInterfaces = clazz.getGenericInterfaces();
        for (Type genericInterface : genericInterfaces) {
            if (((Class) ((ParameterizedType) genericInterface).getRawType()).isAssignableFrom(interfaceClazz)) {
                Type[] types = ((ParameterizedType) genericInterface).getActualTypeArguments();
                return types;
            }
        }
        return null;
    }

    private void resolveBeans() {
        while (!unresolvedBeans.isEmpty()){
            String beanName = unresolvedBeans.iterator().next();
            resolveBean(beanName);
        }
    }

    private void resolveBean(String beanName) {
        BeanDefinition beanDef = findBeanDef(beanName);
        if (beanDef == null){
            throw new UndefinedBeanException("Missing bean definition. (Bean: "+beanName+")");
        }
        if (beanDef.beanResolutionState == BeanResolutionState.IN_PROGRESS){
            throw new CyclicDependencyException("Cyclic dependency found starting with bean: "+beanName);
        }
        if (beanDef.beanResolutionState == BeanResolutionState.RESOLVED){
            return; //bean already resolved.
        }
        beanDef.beanResolutionState = BeanResolutionState.IN_PROGRESS;
        unresolvedBeans.remove(beanName);
        if (beanDef.instance == null){
            beanDef.instance = instantiateBean(beanDef);
        }
        injectFields(beanDef);
        injectSetters(beanDef);
        beanDef.beanResolutionState = BeanResolutionState.RESOLVED;
    }

    private BeanDefinition findBeanDef(String className) {
        List<BeanDefinition> beansForType = beansByType.get(className);
        if (beansForType == null){
            return null;
        }
        if (beansForType.size() > 1){
            throw new AmbigiousBeanDefException("More than one option for bean of type: "+className);
        }
        return beansForType.get(0);
    }

    private void injectSetters(BeanDefinition beanDef) {
        for (Method method : beanDef.beanType.getDeclaredMethods()){
            if (method.getAnnotation(Inject.class) != null){
                Object[] params = resolveDependencies(method.getGenericParameterTypes());
                try {
                    method.setAccessible(true);
                    method.invoke(beanDef.instance, params);
                } catch (IllegalAccessException e) {
                    throw new BeanInstatiationException(e);
                } catch (InvocationTargetException e) {
                    throw new BeanInstatiationException(e);
                }
            }
        }
    }

    private void injectFields(BeanDefinition beanDef) {
        for (Field field : beanDef.beanType.getDeclaredFields()){
            if (field.getAnnotation(Inject.class) != null){
                Object[] valueToInject = resolveDependencies(new Type[]{field.getGenericType()});
                field.setAccessible(true);
                try {
                    field.set(beanDef.instance, valueToInject[0]);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private Object instantiateBean(BeanDefinition beanDef) {
        if (beanDef.factoryBean != null){
            return instantiateBeanFromFactory(beanDef);
        }
        return instantiateBeanFromType(beanDef);
    }

    private Object instantiateBeanFromFactory(BeanDefinition beanDef) {
        resolveBean(beanDef.factoryBean.getName());
        BeanFactory factory = (BeanFactory) getBean(beanDef.factoryBean);
        return factory.createInstance();
    }

    private Object instantiateBeanFromType(BeanDefinition beanDef) {
        Constructor constructorToUse = findConstructorToUse(beanDef);
        Object[] parameters = null;
        if (constructorToUse != null){
            parameters = resolveDependencies(constructorToUse.getGenericParameterTypes());
            constructorToUse.setAccessible(true);
        }
        Object instance = null;
        try {
            if (constructorToUse != null){
                instance = constructorToUse.newInstance(parameters);
            }else{
                instance = beanDef.beanType.newInstance();
            }
        } catch (Exception e) {
            throw new BeanInstatiationException(e);
        }
        return instance;
    }

    private Object[] resolveDependencies(Type[] types) {
        List retVal = new ArrayList();
        for (Type type : types){
            if (type instanceof ParameterizedType){
                resolveParametrizedType(type, retVal);
            }else if (type instanceof Class){
                resolveClass(retVal, (Class) type);
            }
        }
        return retVal.toArray();
    }

    private void resolveClass(List retVal, Class cls) {
        BeanDefinition beanDef = findBeanDef(cls.getName());
        if (beanDef == null){
            throw new UndefinedBeanException("Unable to resolve type: "+cls+". No bean definition that has this type");
        }
        switch (beanDef.beanResolutionState){
            case RESOLVED:
                retVal.add(getBean(cls));
                break;
            case UNRESOLVED:
                resolveBean(cls.getName());
                retVal.add(getBean(cls));
                break;
            case IN_PROGRESS:
                throw new CyclicDependencyException("Found cyclic dependency: During resolution of bean of type: "+cls.getName()+", the same bean was required");
        }
    }

    private void resolveParametrizedType(Type cls, List retVal) {
        ParameterizedType parameterizedType = (ParameterizedType) cls;
        if (!parameterizedType.getRawType().equals(List.class)){
            throw new RuntimeException("Not supported parametrized type: "+cls+". The only type supported is: "+List.class);
        }
        List lstToReturn = new ArrayList();
        Class beanType = (Class) parameterizedType.getActualTypeArguments()[0];
        List<BeanDefinition> beansLst = beansByType.get(beanType.getName());
        for (BeanDefinition beanDef : beansLst){
            switch (beanDef.beanResolutionState){
                case RESOLVED:
                    lstToReturn.add(getBean(beanDef.beanType));
                    break;
                case UNRESOLVED:
                    resolveBean(beanDef.beanType.getName());
                    lstToReturn.add(getBean(beanDef.beanType));
                    break;
                case IN_PROGRESS:
                    throw new CyclicDependencyException("Found cyclic dependency: During resolution of bean of type: "+beanDef.getName()+", the same bean was required");
            }
        }
        retVal.add(lstToReturn);
    }

    private Constructor findConstructorToUse(BeanDefinition beanDef) {
        Constructor constructorToUse = null;
        boolean hasConstructors = false;
        boolean hasMoreThanOneConstructorsWithoutInject = false;
        for (Constructor constructor : beanDef.beanType.getConstructors()){
            if (constructorToUse == null){
                constructorToUse = constructor;
            }else{
                if (constructorToUse.getAnnotation(Inject.class) == null){
                    if (constructor.getAnnotation(Inject.class) != null){
                        constructorToUse = constructor;
                    }else {
                        hasMoreThanOneConstructorsWithoutInject = true;
                    }
                }else{
                    if (constructor.getAnnotation(Inject.class) != null){
                        throw new MissingSuitableConstructorException("Found more than one constructors with @Inject in class:"+beanDef.beanType+"." +
                                " Cannot create instance of this bean");
                    }
                }
            }
        }
        if (constructorToUse == null && hasConstructors){
            throw new MissingSuitableConstructorException("Cannot instantiate bean: "+beanDef.beanType+". The bean must have either no constructor, or single constructor, or single constructor annotated with @Inject");
        }
        if (constructorToUse != null && constructorToUse.getAnnotation(Inject.class) == null){
            if (hasMoreThanOneConstructorsWithoutInject){
                throw new MissingSuitableConstructorException("Cannot instantiate bean of type: "+beanDef.beanType+", because a suitable constructor wasn't found. " +
                        "There should be one of the following: no constructor at all, only one constructor, only one constructor annotated with @Inject");
            }
        }
        return constructorToUse;
    }

    @Override
    public <T> T getBean(Class<T> type) {
        BeanDefinition beanDef = findBeanDef(type.getName());
        if (beanDef == null){
            return null;
        }
        return (T) beanDef.instance;
    }
}
