import com.sample.ApplicationContext;
import com.sample.BeanFactory;
import com.sample.SimpleApplicationContext;
import org.junit.Assert;
import org.junit.Test;
import javax.inject.Inject;
import java.util.List;

/**
 * Created by yotamm on 20/02/16.
 */
public class SimpleApplicationContextTest {

    public static class BeanFactory1 implements BeanFactory<BeanWithTooManyConstructors> {

        final private BeanType1 dep1;

        public BeanFactory1(BeanType1 dep1) {
            this.dep1 = dep1;
        }

        @Override
        public BeanWithTooManyConstructors createInstance() {
            return new BeanWithTooManyConstructors(dep1);
        }
    }

    public static interface  ISomething{}

    public static class BeanWithList {

        private final List<ISomething> iSomethings3;

        @Inject
        public List<ISomething> iSomethings;

        List<ISomething> iSomethings2;

        @Inject
        public void setiSomethings2(List<ISomething> iSomethings){
            iSomethings2 = iSomethings;
        }

        public BeanWithList(BeanType2 beanType2, List<ISomething> iSomethings){
            this.iSomethings3 = iSomethings;
        }


    }

    public static class  BeanWithErrorsInConstructor{
        public BeanWithErrorsInConstructor(){
            Object myObj = null;
            String desc = myObj.toString();
        }
    }

    public static class BeanWithTooManyConstructors{

        private final BeanType1 dep1;
        private final BeanType2 dep2;

        public BeanWithTooManyConstructors(BeanType1 dep1){
            this.dep1 = dep1;
            this.dep2 = null;
        }

        public BeanWithTooManyConstructors(BeanType2 dep2){
            this.dep1 = null;
            this.dep2 = dep2;
        }

    }

    public static class BeanWithTooManyConstructorsWithInject{

        @Inject
        public BeanWithTooManyConstructorsWithInject(BeanType1 dep1){

        }

        @Inject
        public BeanWithTooManyConstructorsWithInject(BeanType2 dep2){

        }

    }

    public static interface IBeanType2 {
        public BeanType1 getBean1();
    }

    public static class BeanType1 implements  ISomething{

    }

    public static class BeanType2 implements IBeanType2, ISomething{
        private final BeanType1 bean1;

        public BeanType2(BeanType1 injected){
            this.bean1 = injected;
        }

        @Override
        public BeanType1 getBean1() {
            return bean1;
        }
    }

    public static class ComplexBean{

        private final BeanType1 bean1;
        private final BeanType2 bean2;

        @Inject
        private BeanType2 beanType2FieldInject;

        private BeanType2 beanType2SetterInject;

        @Inject
        private void setBeanType2SetterInject(BeanType2 value){
            this.beanType2SetterInject = value;
        }

        //This constructor should be ignored since it does not have @Inject
        public ComplexBean(BeanDep1 dep1){
            bean1 = null;
            bean2 = null;
        }

        @Inject
        public ComplexBean(BeanType1 bean1, BeanType2 bean2){
            this.bean1 = bean1;
            this.bean2 = bean2;
        }
    }

    public static class BeanDep1{
        public BeanDep1(BeanDep2 beanDep2){

        }
    }

    public static class BeanDep2{
        public BeanDep2(BeanDep1 beanDep1){

        }
    }

    @Test
    public void testSingletones(){
        BeanType1 singleton = new BeanType1();
        ApplicationContext ctx = new SimpleApplicationContext(new Class[]{}, new Object[]{
                singleton
        });
        Assert.assertEquals(singleton, ctx.getBean(BeanType1.class));
    }


    @Test
    public void injectionTest(){
        BeanType1 singleton = new BeanType1();
        ApplicationContext ctx = new SimpleApplicationContext(new Class[]{
                BeanType2.class
        }, new Object[]{
                singleton
        });
        BeanType2 bean2 = ctx.getBean(BeanType2.class);
        Assert.assertNotNull(bean2);
        Assert.assertNotNull(bean2.bean1);
    }

    @Test
    public void listInjectionTest(){
        ApplicationContext ctx = new SimpleApplicationContext(new Class[]{
                BeanType2.class,
                BeanType1.class,
                BeanWithList.class
        }, new Object[]{
        });
        BeanType2 bean2 = ctx.getBean(BeanType2.class);
        Assert.assertNotNull(bean2);
        Assert.assertNotNull(bean2.bean1);
        BeanWithList beanWithList = ctx.getBean(BeanWithList.class);
        Assert.assertNotNull(beanWithList);
        Assert.assertNotNull(beanWithList.iSomethings);
        Assert.assertEquals(2, beanWithList.iSomethings.size());
        Assert.assertTrue(beanWithList.iSomethings.contains(bean2));
        Assert.assertTrue(beanWithList.iSomethings.contains(bean2.bean1));
        Assert.assertEquals(beanWithList.iSomethings, beanWithList.iSomethings2);
        Assert.assertEquals(beanWithList.iSomethings, beanWithList.iSomethings3);
    }

    @Test
    public void complexInjectionTest(){
        ApplicationContext ctx = new SimpleApplicationContext(new Class[]{
                BeanType2.class,
                BeanType1.class,
                ComplexBean.class
        }, new Object[]{
        });

        BeanType1 bean1 = ctx.getBean(BeanType1.class);
        Assert.assertNotNull(bean1);

        BeanType2 bean2 = ctx.getBean(BeanType2.class);
        Assert.assertNotNull(bean2);
        Assert.assertNotNull(bean2.bean1);
        Assert.assertEquals(bean1, bean2.bean1);

        ComplexBean complexBean = ctx.getBean(ComplexBean.class);
        Assert.assertEquals(bean1, complexBean.bean1);
        Assert.assertEquals(bean2, complexBean.bean2);

        //Test field injection
        Assert.assertNotNull(complexBean.beanType2FieldInject);
        Assert.assertEquals(bean2, complexBean.beanType2FieldInject);

        //Test setter injection
        Assert.assertNotNull(complexBean.beanType2SetterInject);
        Assert.assertEquals(bean2, complexBean.beanType2SetterInject);
    }

    @Test
    public void testBeanFactory(){
        ApplicationContext ctx = new SimpleApplicationContext(new Class[]{
                BeanType2.class,
                BeanType1.class,
                ComplexBean.class,
                BeanFactory1.class
        }, new Object[]{
        });

        BeanType1 bean1 = ctx.getBean(BeanType1.class);
        Assert.assertNotNull(bean1);

        BeanType2 bean2 = ctx.getBean(BeanType2.class);
        Assert.assertNotNull(bean2);
        Assert.assertNotNull(bean2.bean1);
        Assert.assertEquals(bean1, bean2.bean1);

        IBeanType2 iBeanType2 = ctx.getBean(IBeanType2.class);
        Assert.assertNotNull(iBeanType2);
        Assert.assertEquals(bean1, iBeanType2.getBean1());

        ComplexBean complexBean = ctx.getBean(ComplexBean.class);
        Assert.assertEquals(bean1, complexBean.bean1);
        Assert.assertEquals(bean2, complexBean.bean2);

        BeanWithTooManyConstructors beanWithTooManyConstructors = ctx.getBean(BeanWithTooManyConstructors.class);
        Assert.assertNotNull(beanWithTooManyConstructors);
        Assert.assertEquals(bean1, beanWithTooManyConstructors.dep1);
    }

    //Negative tests...

    @Test(expected = SimpleApplicationContext.CyclicDependencyException.class)
    public void cyclicDepdendencyCheck(){
        ApplicationContext ctx = new SimpleApplicationContext(new Class[]{
                BeanType2.class,
                BeanType1.class,
                ComplexBean.class,
                BeanDep1.class,
                BeanDep2.class
        }, new Object[]{
        });
    }

    @Test(expected = SimpleApplicationContext.MissingSuitableConstructorException.class)
    public void checkTooManyConstructors(){
        ApplicationContext ctx = new SimpleApplicationContext(new Class[]{
                BeanWithTooManyConstructors.class,
                BeanType2.class,
                BeanType1.class
        }, new Object[]{
        });
    }

    @Test(expected = SimpleApplicationContext.MissingSuitableConstructorException.class)
    public void checkTooManyConstructorsWithInject(){
        ApplicationContext ctx = new SimpleApplicationContext(new Class[]{
                BeanWithTooManyConstructorsWithInject.class,
                BeanType2.class,
                BeanType1.class
        }, new Object[]{
        });
    }

    @Test(expected = SimpleApplicationContext.UndefinedBeanException.class)
    public void testUnableToResolve(){
        ApplicationContext ctx = new SimpleApplicationContext(new Class[]{
                BeanType2.class,
        }, new Object[]{
        });
    }

    @Test(expected = SimpleApplicationContext.BeanInstatiationException.class)
    public void testBeanWithErrorsInConstructor(){
        ApplicationContext ctx = new SimpleApplicationContext(new Class[]{
                BeanWithErrorsInConstructor.class,
        }, new Object[]{
        });
    }
}
