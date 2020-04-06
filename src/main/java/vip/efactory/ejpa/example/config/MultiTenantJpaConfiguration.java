package vip.efactory.ejpa.example.config;

import lombok.AllArgsConstructor;
import org.hibernate.MultiTenancyStrategy;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Environment;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.hibernate.tool.schema.Action;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.hibernate5.HibernateTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import vip.efactory.ejpa.example.entity.SysTenant;
import vip.efactory.ejpa.tenant.database.MultiTenantConnectionProviderImpl;
import vip.efactory.ejpa.tenant.database.MultiTenantIdentifierResolver;
import vip.efactory.ejpa.tenant.database.TenantDataSourceProvider;
import vip.efactory.ejpa.tenant.identifier.TenantConstants;

import javax.annotation.PostConstruct;
import javax.persistence.EntityManagerFactory;
import java.util.LinkedHashMap;
import java.util.Map;

@AllArgsConstructor
@Configuration
@EnableConfigurationProperties({JpaProperties.class})
@EnableTransactionManagement(proxyTargetClass = true)
@EnableJpaRepositories(basePackages = {"vip.efactory.ejpa.example.repository"}, transactionManagerRef = "txManager")
public class MultiTenantJpaConfiguration {

    private JpaProperties jpaProperties;  // yml文件中的jpa配置
    private DataSourceProperties dataSourceProperties; // yml配置文件里的数据源，也就是租户数据表所在的数据源

    /**
     * 初始化所有租户的数据源
     */
    @PostConstruct
    public void initDataSources() {
        // 先初始化租户表所在的数据源，然后从租户表中读取其他租户的数据源然后再进行初始化,详见：DataSourceBeanPostProcessor类
        DataSourceBuilder factory = DataSourceBuilder.create()
                .url(dataSourceProperties.getUrl())
                .username(dataSourceProperties.getUsername())
                .password(dataSourceProperties.getPassword())
                .driverClassName(dataSourceProperties.getDriverClassName());
        TenantDataSourceProvider.addDataSource(TenantConstants.DEFAULT_TENANT_ID.toString(), factory.build());  // 放入数据源集合中
    }

    @Bean
    public MultiTenantConnectionProvider multiTenantConnectionProvider() {
        return new MultiTenantConnectionProviderImpl();
    }

    @Bean
    public CurrentTenantIdentifierResolver currentTenantIdentifierResolver() {
        return new MultiTenantIdentifierResolver();
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactoryBean(MultiTenantConnectionProvider multiTenantConnectionProvider,
                                                                           CurrentTenantIdentifierResolver currentTenantIdentifierResolver) {

        Map<String, Object> hibernateProps = new LinkedHashMap<>();
        hibernateProps.putAll(this.jpaProperties.getProperties());
        hibernateProps.put(Environment.MULTI_TENANT, MultiTenancyStrategy.DATABASE); // 使用基于独立数据库的多租户模式
        hibernateProps.put(Environment.PHYSICAL_NAMING_STRATEGY, "org.springframework.boot.orm.jpa.hibernate.SpringPhysicalNamingStrategy"); //属性及column命名策略
        hibernateProps.put(Environment.MULTI_TENANT_CONNECTION_PROVIDER, multiTenantConnectionProvider);
        hibernateProps.put(Environment.MULTI_TENANT_IDENTIFIER_RESOLVER, currentTenantIdentifierResolver);
        hibernateProps.put(Environment.HBM2DDL_AUTO, Action.UPDATE); // 自动更新表结构,仅默认数据源有效且控制台会报警告可以不用管！
        hibernateProps.put(Environment.SHOW_SQL, true);     // 显示SQL
        hibernateProps.put(Environment.FORMAT_SQL, true);   // 格式化SQL

        // No dataSource is set to resulting entityManagerFactoryBean
        LocalContainerEntityManagerFactoryBean result = new LocalContainerEntityManagerFactoryBean();

        result.setPackagesToScan(new String[]{SysTenant.class.getPackage().getName()});
        result.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        result.setJpaPropertyMap(hibernateProps);

        return result;
    }

    @Bean
    @Primary  // 注意我们自己定义的bean，最好都加此注解，防止与自动配置的重复而不知道如何选择
    public EntityManagerFactory entityManagerFactory(LocalContainerEntityManagerFactoryBean entityManagerFactoryBean) {
        return entityManagerFactoryBean.getObject();
    }

    @Bean
    @Primary  // 注意我们自己定义的bean，最好都加此注解，防止与自动配置的重复而不知道如何选择
    public PlatformTransactionManager txManager(EntityManagerFactory entityManagerFactory) {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        HibernateTransactionManager result = new HibernateTransactionManager();
        result.setAutodetectDataSource(false);   // 不自动检测数据源
        result.setSessionFactory(sessionFactory);
        result.setRollbackOnCommitFailure(true);
        return result;
    }
}
