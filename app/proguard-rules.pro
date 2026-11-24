# SMBJ 0.15 selects NTLM on Android. Its Kerberos implementation is JVM-only.
-dontwarn org.ietf.jgss.GSSContext
-dontwarn org.ietf.jgss.GSSCredential
-dontwarn org.ietf.jgss.GSSException
-dontwarn org.ietf.jgss.GSSManager
-dontwarn org.ietf.jgss.GSSName
-dontwarn org.ietf.jgss.Oid

# SMBJ's MBassador subscriptions use typed events without expression filters.
-dontwarn javax.el.BeanELResolver
-dontwarn javax.el.ELContext
-dontwarn javax.el.ELResolver
-dontwarn javax.el.ExpressionFactory
-dontwarn javax.el.FunctionMapper
-dontwarn javax.el.ValueExpression
-dontwarn javax.el.VariableMapper

# MBassador discovers these subscribers by reflection.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keepclassmembers,allowobfuscation class com.hierynomus.smbj.** {
    @net.engio.mbassy.listener.Handler <methods>;
}
