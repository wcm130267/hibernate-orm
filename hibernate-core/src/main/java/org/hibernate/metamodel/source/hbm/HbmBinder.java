/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) 2010, Red Hat Inc. or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.  All third-party contributions are
 * distributed under license by Red Hat Inc.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.hibernate.metamodel.source.hbm;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hibernate.cfg.NamingStrategy;
import org.hibernate.engine.spi.FilterDefinition;
import org.hibernate.internal.util.StringHelper;
import org.hibernate.metamodel.relational.AuxiliaryDatabaseObject;
import org.hibernate.metamodel.binding.FetchProfile;
import org.hibernate.metamodel.binding.FetchProfile.Fetch;
import org.hibernate.metamodel.binding.TypeDef;
import org.hibernate.metamodel.domain.MetaAttribute;
import org.hibernate.metamodel.relational.BasicAuxiliaryDatabaseObjectImpl;
import org.hibernate.metamodel.source.MappingException;
import org.hibernate.metamodel.source.Origin;
import org.hibernate.metamodel.source.hbm.util.MappingHelper;
import org.hibernate.metamodel.source.hbm.xml.mapping.XMLFetchProfileElement;
import org.hibernate.metamodel.source.hbm.xml.mapping.XMLFetchProfileElement.XMLFetch;
import org.hibernate.metamodel.source.hbm.xml.mapping.XMLHibernateMapping;
import org.hibernate.metamodel.source.hbm.xml.mapping.XMLHibernateMapping.XMLClass;
import org.hibernate.metamodel.source.hbm.xml.mapping.XMLHibernateMapping.XMLImport;
import org.hibernate.metamodel.source.hbm.xml.mapping.XMLJoinedSubclassElement;
import org.hibernate.metamodel.source.hbm.xml.mapping.XMLParamElement;
import org.hibernate.metamodel.source.hbm.xml.mapping.XMLQueryElement;
import org.hibernate.metamodel.source.hbm.xml.mapping.XMLSqlQueryElement;
import org.hibernate.metamodel.source.hbm.xml.mapping.XMLSubclassElement;
import org.hibernate.metamodel.source.hbm.xml.mapping.XMLUnionSubclassElement;
import org.hibernate.metamodel.source.internal.JaxbRoot;
import org.hibernate.metamodel.source.spi.MetadataImplementor;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.service.classloading.spi.ClassLoaderService;
import org.hibernate.service.classloading.spi.ClassLoadingException;
import org.hibernate.type.Type;

/**
 * Responsible for performing binding of hbm xml.
 */
public class HbmBinder implements MappingDefaults {
	private static final String DEFAULT_IDENTIFIER_COLUMN_NAME = "id";
	private static final String DEFAULT_DISCRIMINATOR_COLUMN_NAME = "class";

	private final JaxbRoot<XMLHibernateMapping> jaxbRoot;
	private final XMLHibernateMapping hibernateMapping;

	private final String defaultSchemaName;
	private final String defaultCatalogName;
	private final String defaultCascade;
	private final String defaultAccess;
	private final boolean defaultLazy;
	private final String packageName;
	private final boolean autoImport;

	private final MetadataImplementor metadata;
	private final Map<String, MetaAttribute> globalMetas;

	private Map<String, MetaAttribute> mappingMetas;

	public HbmBinder(MetadataImplementor metadata, Map<String, MetaAttribute> globalMetas, JaxbRoot<XMLHibernateMapping> jaxbRoot) {
		this.jaxbRoot = jaxbRoot;
		this.hibernateMapping = jaxbRoot.getRoot();

		this.metadata = metadata;
		this.globalMetas = globalMetas;

		defaultSchemaName = hibernateMapping.getSchema();
		defaultCatalogName = hibernateMapping.getCatalog();
		defaultCascade = MappingHelper.getStringValue( hibernateMapping.getDefaultCascade(), "none" );
		defaultAccess = MappingHelper.getStringValue( hibernateMapping.getDefaultAccess(), "property" );
		defaultLazy = hibernateMapping.isDefaultLazy();
		packageName = hibernateMapping.getPackage();
		autoImport = hibernateMapping.isAutoImport();

		mappingMetas = HbmHelper.extractMetas( hibernateMapping.getMeta(), true, globalMetas );
	}

	public MetadataImplementor getMetadata() {
		return metadata;
	}

	Origin getOrigin() {
		return jaxbRoot.getOrigin();
	}

	public String getDefaultSchemaName() {
		return defaultSchemaName;
	}

	public String getDefaultCatalogName() {
		return defaultCatalogName;
	}

	public String getDefaultIdColumnName() {
		return DEFAULT_IDENTIFIER_COLUMN_NAME;
	}

	public String getDefaultDiscriminatorColumnName() {
		return DEFAULT_DISCRIMINATOR_COLUMN_NAME;
	}

	public String getDefaultCascade() {
		return defaultCascade;
	}

	public String getDefaultAccess() {
		return defaultAccess;
	}

	public boolean isDefaultLazy() {
		return defaultLazy;
	}

	@Override
	public ServiceRegistry getServiceRegistry() {
		return metadata.getServiceRegistry();
	}

	public NamingStrategy getNamingStrategy() {
		return metadata.getOptions().getNamingStrategy();
	}

	public String getPackageName() {
		return packageName;
	}

	boolean isAutoImport() {
		return autoImport;
	}

	public Map<String, MetaAttribute> getMappingMetas() {
		return mappingMetas;
	}

	public void processHibernateMapping() {
		// perform bindings with no pre-requisites
		bindDatabaseObjectDefinitions();
		bindTypeDefinitions();

		bindFilterDefinitions();
		bindIdentifierGenerators();

		if ( hibernateMapping.getClazzOrSubclassOrJoinedSubclass() != null ) {
			for ( Object clazzOrSubclass : hibernateMapping.getClazzOrSubclassOrJoinedSubclass() ) {
				if ( XMLClass.class.isInstance( clazzOrSubclass ) ) {
					XMLClass clazz =
							XMLClass.class.cast( clazzOrSubclass );
					new RootEntityBinder( this, clazz ).process( clazz );
				}
				else if ( XMLSubclassElement.class.isInstance( clazzOrSubclass ) ) {
//					PersistentClass superModel = getSuperclass( mappings, element );
//					handleSubclass( superModel, mappings, element, inheritedMetas );
				}
				else if ( XMLJoinedSubclassElement.class.isInstance( clazzOrSubclass ) ) {
//					PersistentClass superModel = getSuperclass( mappings, element );
//					handleJoinedSubclass( superModel, mappings, element, inheritedMetas );
				}
				else if ( XMLUnionSubclassElement.class.isInstance( clazzOrSubclass ) ) {
//					PersistentClass superModel = getSuperclass( mappings, element );
//					handleUnionSubclass( superModel, mappings, element, inheritedMetas );
				}
				else {
					throw new org.hibernate.metamodel.source.MappingException(
							"unknown type of class or subclass: " +
									clazzOrSubclass.getClass().getName(), jaxbRoot.getOrigin()
					);
				}
			}
		}

		bindFetchProfiles();
		bindImports();

		if ( hibernateMapping.getQueryOrSqlQuery() != null ) {
			for ( Object queryOrSqlQuery : hibernateMapping.getQueryOrSqlQuery() ) {
				if ( XMLQueryElement.class.isInstance( queryOrSqlQuery ) ) {
//					bindNamedQuery( element, null, mappings );
				}
				else if ( XMLSqlQueryElement.class.isInstance( queryOrSqlQuery ) ) {
//				bindNamedSQLQuery( element, null, mappings );
				}
				else {
					throw new MappingException(
							"unknown type of query: " +
									queryOrSqlQuery.getClass().getName(), jaxbRoot.getOrigin()
					);
				}
			}
		}
		if ( hibernateMapping.getResultset() != null ) {
//			bindResultSetMappingDefinitions( element, null, mappings );
		}
	}

	private void bindDatabaseObjectDefinitions() {
		if ( hibernateMapping.getDatabaseObject() == null ) {
			return;
		}
		for ( XMLHibernateMapping.XMLDatabaseObject databaseObjectElement : hibernateMapping.getDatabaseObject() ) {
			final AuxiliaryDatabaseObject auxiliaryDatabaseObject;
			if ( databaseObjectElement.getDefinition() != null ) {
				final String className = databaseObjectElement.getDefinition().getClazz();
				try {
					auxiliaryDatabaseObject = (AuxiliaryDatabaseObject) classLoaderService().classForName( className ).newInstance();
				}
				catch (ClassLoadingException e) {
					throw e;
				}
				catch (Exception e) {
					throw new MappingException(
							"could not instantiate custom database object class [" + className + "]",
							jaxbRoot.getOrigin()
					);
				}
			}
			else {
				Set<String> dialectScopes = new HashSet<String>();
				if ( databaseObjectElement.getDialectScope() != null ) {
					for ( XMLHibernateMapping.XMLDatabaseObject.XMLDialectScope dialectScope : databaseObjectElement.getDialectScope() ) {
						dialectScopes.add( dialectScope.getName() );
					}
				}
				auxiliaryDatabaseObject = new BasicAuxiliaryDatabaseObjectImpl(
						databaseObjectElement.getCreate(),
						databaseObjectElement.getDrop(),
						dialectScopes
				);
			}
			metadata.addAuxiliaryDatabaseObject( auxiliaryDatabaseObject );
		}
	}

	private void bindTypeDefinitions() {
		if ( hibernateMapping.getTypedef() == null ) {
			return;
		}
		for ( XMLHibernateMapping.XMLTypedef typedef : hibernateMapping.getTypedef() ) {
			final Map<String, String> parameters = new HashMap<String, String>();
			for ( XMLParamElement paramElement : typedef.getParam() ) {
				parameters.put( paramElement.getName(), paramElement.getValue() );
			}
			metadata.addTypeDefinition( new TypeDef( typedef.getName(), typedef.getClazz(), parameters ) );
		}
	}

	private void bindIdentifierGenerators() {
		if ( hibernateMapping.getIdentifierGenerator() == null ) {
			return;
		}
		for ( XMLHibernateMapping.XMLIdentifierGenerator identifierGeneratorElement : hibernateMapping.getIdentifierGenerator() ) {
			metadata.registerIdentifierGenerator(
					identifierGeneratorElement.getName(),
					identifierGeneratorElement.getClazz()
			);
		}
	}

	private void bindImports() {
		if ( hibernateMapping.getImport() == null ) {
			return;
		}
		for ( XMLImport importValue : hibernateMapping.getImport() ) {
			String className = getClassName( importValue.getClazz() );
			String rename = importValue.getRename();
			rename = ( rename == null ) ? StringHelper.unqualify( className ) : rename;
			metadata.addImport( className, rename );
		}
	}

	private void bindFetchProfiles(){
		if(hibernateMapping.getFetchProfile() == null){
			return;
		}
		bindFetchProfiles( hibernateMapping.getFetchProfile(),null );
	}

	protected void bindFetchProfiles(List<XMLFetchProfileElement> fetchProfiles, String containingEntityName) {
		for ( XMLFetchProfileElement fetchProfile : fetchProfiles ) {
			String profileName = fetchProfile.getName();
			Set<Fetch> fetches = new HashSet<Fetch>();
			for ( XMLFetch fetch : fetchProfile.getFetch() ) {
				String entityName = fetch.getEntity() == null ? containingEntityName : fetch.getEntity();
				if ( entityName == null ) {
					throw new MappingException(
							"could not determine entity for fetch-profile fetch [" + profileName + "]:[" +
									fetch.getAssociation() + "]",
							jaxbRoot.getOrigin()
					);
				}
				fetches.add( new Fetch( entityName, fetch.getAssociation(), fetch.getStyle() ) );
			}
			metadata.addFetchProfile( new FetchProfile( profileName, fetches ) );
		}
	}

	private void bindFilterDefinitions() {
		if(hibernateMapping.getFilterDef() == null){
			return;
		}
		for ( XMLHibernateMapping.XMLFilterDef filterDefinition : hibernateMapping.getFilterDef() ) {
			final String name = filterDefinition.getName();
			final Map<String,Type> parameters = new HashMap<String, Type>();
			String condition = null;
			for ( Object o : filterDefinition.getContent() ) {
				if ( o instanceof String ) {
					// represents the condition
					if ( condition != null ) {
						// log?
					}
					condition = (String) o;
				}
				else if ( o instanceof XMLHibernateMapping.XMLFilterDef.XMLFilterParam ) {
					final XMLHibernateMapping.XMLFilterDef.XMLFilterParam paramElement = (XMLHibernateMapping.XMLFilterDef.XMLFilterParam) o;
					// todo : should really delay this resolution until later to allow typedef names
					parameters.put(
							paramElement.getName(),
							metadata.getTypeResolver().heuristicType( paramElement.getType() )
					);
				}
				else {
					throw new MappingException( "Unrecognized nested filter content", jaxbRoot.getOrigin() );
				}
			}
			if ( condition == null ) {
				condition = filterDefinition.getCondition();
			}
			metadata.addFilterDefinition( new FilterDefinition( name, condition, parameters ) );
		}
	}

	private ClassLoaderService classLoaderService;

	private ClassLoaderService classLoaderService() {
		if ( classLoaderService == null ) {
			classLoaderService = metadata.getServiceRegistry().getService( ClassLoaderService.class );
		}
		return classLoaderService;
	}

	String extractEntityName(XMLClass entityClazz) {
		return HbmHelper.extractEntityName( entityClazz, packageName );
	}

	String getClassName(String unqualifiedName) {
		return HbmHelper.getClassName( unqualifiedName, packageName );
	}
}