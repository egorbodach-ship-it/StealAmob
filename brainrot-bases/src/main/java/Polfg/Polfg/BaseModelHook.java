package Polfg.Polfg;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Мостик к ModelEngine R4 для мобов, которые уже стоят на базе.
 *
 * Это осознанная копия ModelEngineHook из brainrot-spawner, а не общий класс:
 * плагины грузятся разными класслоадерами, и один и тот же FQCN в двух jar-ах
 * приводит к тому, что каждый плагин видит свою копию статических карт — то есть
 * к молчаливой путанице. Разные имена классов эту грабельку убирают.
 *
 * Всё через рефлексию: ME лежит в приватном репозитории Lumine, до которого
 * GitHub Actions не достаёт. Нет ModelEngine на сервере — мобы просто остаются
 * ванильными, ни одной ошибки в консоли.
 */
public final class BaseModelHook {

    private static final String API_CLASS = "com.ticxo.modelengine.api.ModelEngineAPI";
    private static final String LOG = "[BRAINROT/BASE-ME] ";

    private static boolean probed = false;
    private static boolean available = false;

    private static Class<?> apiClass;
    private static Method mCreateActiveModel;
    private static Method mCreateModeledEntity;
    private static Method mRemoveModeledEntity;
    private static Method mGetModeledEntity;

    /** UUID сущности → ActiveModel */
    private static final Map<UUID, Object> MODELS = new ConcurrentHashMap<>();
    /** UUID сущности → ModeledEntity */
    private static final Map<UUID, Object> RIGS = new ConcurrentHashMap<>();
    /** UUID сущности → id блюпринта, чтобы вернуть модель, если ME её потерял. */
    private static final Map<UUID, String> BLUEPRINTS = new ConcurrentHashMap<>();
    /** Счётчик попыток перецепить: три промаха — сдаёмся молча. */
    private static final Map<UUID, Integer> REATTACH_TRIES = new ConcurrentHashMap<>();

    private static final java.util.Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private BaseModelHook() {}

    private static void warnOnce(String key, String message) {
        if (WARNED.add(key)) Bukkit.getLogger().warning(LOG + message);
    }

    // ── доступность ───────────────────────────────────────────────────────

    public static boolean isAvailable() {
        if (!probed) probe();
        return available;
    }

    private static synchronized void probe() {
        if (probed) return;
        probed = true;
        try {
            if (Bukkit.getPluginManager().getPlugin("ModelEngine") == null) {
                Bukkit.getLogger().info(LOG + "плагин ModelEngine не найден, модели на базе отключены");
                return;
            }
            apiClass = Class.forName(API_CLASS);
            mCreateActiveModel = findStatic(apiClass, "createActiveModel", 1);
            mCreateModeledEntity = findStatic(apiClass, "createModeledEntity", 1);
            mRemoveModeledEntity = findStatic(apiClass, "removeModeledEntity", 1);
            mGetModeledEntity = findStatic(apiClass, "getModeledEntity", 1);
            if (mCreateActiveModel == null || mCreateModeledEntity == null) {
                Bukkit.getLogger().warning(LOG + "ModelEngine есть, но API незнакомый — модели отключены");
                return;
            }
            available = true;
            Bukkit.getLogger().info(LOG + "ModelEngine подключён, мобы на базе будут с моделями");
        } catch (ClassNotFoundException e) {
            Bukkit.getLogger().warning(LOG + "класс " + API_CLASS + " не найден — нужен ModelEngine R4");
        } catch (Throwable t) {
            Bukkit.getLogger().warning(LOG + "не удалось подключиться к ModelEngine: " + t);
        }
    }

    // ── навесить и снять ──────────────────────────────────────────────────

    /**
     * Навешивает блюпринт и прячет ванильную сущность-подложку.
     *
     * @param blueprint имя файла в plugins/ModelEngine/blueprints без расширения
     */
    public static boolean attach(Entity mob, String blueprint) {
        if (mob == null || blueprint == null || blueprint.isEmpty()) return false;
        if (!isAvailable()) return false;
        UUID id = mob.getUniqueId();
        try {
            Object model = mCreateActiveModel.invoke(null, blueprint.toLowerCase(Locale.ROOT));
            if (model == null) {
                warnOnce("bp-" + blueprint, "блюпринт \"" + blueprint + "\" не загружен — "
                        + "проверь plugins/ModelEngine/blueprints и сделай /meg reload");
                return false;
            }
            Object rig = mCreateModeledEntity.invoke(null, mob);
            if (rig == null) {
                warnOnce("rig-null", "createModeledEntity вернул null для " + mob.getType());
                return false;
            }

            Object attached;
            Method add2 = findMethod(rig.getClass(), "addModel", 2);
            if (add2 != null) {
                attached = add2.invoke(rig, buildAddModelArgs(add2.getParameterTypes(), model, true));
            } else {
                Method add1 = findMethod(rig.getClass(), "addModel", 1);
                if (add1 == null) {
                    warnOnce("no-addmodel", "у ModeledEntity нет addModel — версия ME не поддерживается");
                    return false;
                }
                attached = add1.invoke(rig, model);
            }
            Object live = unwrap(attached);
            if (live != null && live.getClass() == model.getClass()) model = live;

            // Авторитетно берём у рига тот экземпляр, который он реально держит:
            // addModel в части сборок клонирует ActiveModel, и анимации на нашей
            // копии тогда уходят в никуда.
            Object owned = modelFromRig(rig, blueprint);
            if (owned != null) model = owned;

            Method vis = findMethod(rig.getClass(), "setBaseEntityVisible", 1);
            if (vis != null) vis.invoke(rig, false);

            MODELS.put(id, model);
            RIGS.put(id, rig);
            BLUEPRINTS.put(id, blueprint.toLowerCase(Locale.ROOT));
            REATTACH_TRIES.remove(id);
            return true;
        } catch (Throwable t) {
            warnOnce("attach-" + blueprint,
                    "не удалось навесить \"" + blueprint + "\": " + rootCause(t));
            MODELS.remove(id);
            RIGS.remove(id);
            return false;
        }
    }

    private static Object modelFromRig(Object rig, String blueprint) {
        if (rig == null) return null;
        String key = blueprint == null ? null : blueprint.toLowerCase(Locale.ROOT);
        try {
            Method one = findMethod(rig.getClass(), "getModel", 1);
            if (one != null && key != null && one.getParameterTypes()[0] == String.class) {
                Object res = unwrap(one.invoke(rig, key));
                if (res != null) return res;
            }
            Method all = findMethod(rig.getClass(), "getModels", 0);
            if (all != null) {
                Object res = all.invoke(rig);
                if (res instanceof Map<?, ?> map) {
                    if (key != null) {
                        for (Map.Entry<?, ?> e : map.entrySet()) {
                            if (String.valueOf(e.getKey()).equalsIgnoreCase(key) && e.getValue() != null) {
                                return e.getValue();
                            }
                        }
                    }
                    for (Object v : map.values()) if (v != null) return v;
                } else if (res instanceof Iterable<?> it) {
                    for (Object v : it) if (v != null) return v;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * Проверяет, жива ли модель, и при необходимости навешивает заново.
     * Это же лечит перезапуск сервера и кражу мобов: sweep-таск базы зовёт
     * ensureAttached, и модель возвращается сама.
     */
    public static boolean ensureAttached(Entity mob, String blueprint) {
        if (mob == null || !mob.isValid() || mob.isDead()) return false;
        if (!isAvailable()) return false;
        UUID id = mob.getUniqueId();
        String bp = blueprint != null ? blueprint.toLowerCase(Locale.ROOT) : BLUEPRINTS.get(id);
        if (bp == null) return MODELS.containsKey(id);
        if (rigAlive(id) && MODELS.get(id) != null) {
            REATTACH_TRIES.remove(id);
            return true;
        }
        int tries = REATTACH_TRIES.getOrDefault(id, 0);
        if (tries >= 3) return false;
        REATTACH_TRIES.put(id, tries + 1);
        Object stale = RIGS.remove(id);
        MODELS.remove(id);
        if (stale != null) {
            try {
                Method destroy = findMethod(stale.getClass(), "destroy", 0);
                if (destroy != null) destroy.invoke(stale);
            } catch (Throwable ignored) {}
        }
        boolean ok = attach(mob, bp);
        if (ok && tries > 0) {
            Bukkit.getLogger().info(LOG + "модель \"" + bp + "\" навешена заново (попытка " + (tries + 1) + ")");
        } else if (!ok && tries + 1 >= 3) {
            warnOnce("reattach-fail-" + bp, "не смог вернуть модель \"" + bp
                    + "\" после трёх попыток — моб останется ванильным");
        }
        return ok;
    }

    private static boolean rigAlive(UUID id) {
        Object rig = RIGS.get(id);
        if (rig == null) return false;
        try {
            Method removed = findMethod(rig.getClass(), "isRemoved", 0);
            if (removed == null) removed = findMethod(rig.getClass(), "isDestroyed", 0);
            if (removed != null) {
                Object res = removed.invoke(rig);
                if (res instanceof Boolean gone && gone) return false;
            }
        } catch (Throwable ignored) {}
        if (mGetModeledEntity != null) {
            try {
                if (mGetModeledEntity.getParameterTypes()[0] == UUID.class) {
                    return unwrap(mGetModeledEntity.invoke(null, id)) != null;
                }
            } catch (Throwable ignored) {}
        }
        return true;
    }

    /** Снимает модель. Обязательно, иначе ME оставит рига без хозяина. */
    public static void detach(Entity mob) {
        if (mob == null) return;
        detach(mob.getUniqueId());
    }

    public static void detach(UUID id) {
        if (id == null) return;
        Object rig = RIGS.remove(id);
        MODELS.remove(id);
        BLUEPRINTS.remove(id);
        REATTACH_TRIES.remove(id);
        if (rig == null) return;
        try {
            Method destroy = findMethod(rig.getClass(), "destroy", 0);
            if (destroy != null) { destroy.invoke(rig); return; }
            if (mRemoveModeledEntity != null) mRemoveModeledEntity.invoke(null, id);
        } catch (Throwable t) {
            warnOnce("detach", "не удалось снять модель: " + rootCause(t));
        }
    }

    /** Снять всё — для onDisable, чтобы после /reload не осталось висячих ригов. */
    public static void detachAll() {
        for (UUID id : new java.util.ArrayList<>(RIGS.keySet())) detach(id);
        MODELS.clear();
        BLUEPRINTS.clear();
        REATTACH_TRIES.clear();
    }

    /** Знаем ли мы про модель на этой сущности (помним блюпринт — значит да). */
    public static boolean hasModel(Entity mob) {
        if (mob == null) return false;
        UUID id = mob.getUniqueId();
        return MODELS.containsKey(id) || BLUEPRINTS.containsKey(id);
    }

    public static int tracked() {
        return BLUEPRINTS.size();
    }

    // ── анимации ──────────────────────────────────────────────────────────

    /** Одноразовая анимация, перебивает текущую (spawn/hurt/death). */
    public static boolean playForced(Entity mob, String animation) {
        return play(mob, animation, 0.0, 0.2, 1.0, true, false);
    }

    /** Зайти в зацикленную анимацию. force=true: иначе ME мягкий запрос игнорит. */
    public static boolean loop(Entity mob, String animation) {
        return play(mob, animation, 0.2, 0.2, 1.0, true, false);
    }

    /**
     * Страховка цикла: сначала убеждаемся, что модель на месте, потом спрашиваем
     * ME, играет ли нужная анимация. Точно не играет — поднимаем с force; играет —
     * не трогаем (перезапуск идущей анимации виден как дёрганый кадр); ME не
     * ответил — по старой мягкой схеме.
     */
    public static boolean keepAlive(Entity mob, String animation, String blueprint) {
        if (mob == null || animation == null) return false;
        if (!ensureAttached(mob, blueprint)) return false;
        Boolean playing = isPlaying(mob, animation);
        if (Boolean.TRUE.equals(playing)) return true;
        boolean force = Boolean.FALSE.equals(playing);
        return play(mob, animation, 0.2, 0.2, 1.0, force, true);
    }

    /** null — эта сборка ME не умеет отвечать на вопрос «что играется». */
    public static Boolean isPlaying(Entity mob, String animation) {
        if (mob == null || animation == null) return null;
        Object model = MODELS.get(mob.getUniqueId());
        if (model == null) return Boolean.FALSE;
        try {
            Method getHandler = findMethod(model.getClass(), "getAnimationHandler", 0);
            if (getHandler == null) return null;
            Object handler = getHandler.invoke(model);
            if (handler == null) return null;
            for (String name : new String[]{"isPlayingAnimation", "isPlaying"}) {
                Method m = findMethod(handler.getClass(), name, 1);
                if (m != null && m.getParameterTypes()[0] == String.class) {
                    Object res = m.invoke(handler, animation);
                    if (res instanceof Boolean b) return b;
                }
            }
            for (String name : new String[]{"getPlayingAnimations", "getAnimations", "getAnimation"}) {
                Method m = findMethod(handler.getClass(), name, 0);
                if (m == null) continue;
                Object res = m.invoke(handler);
                if (res == null) continue;
                if (res instanceof Map<?, ?> map) {
                    for (Object k : map.keySet()) {
                        if (String.valueOf(k).equalsIgnoreCase(animation)) return Boolean.TRUE;
                    }
                    return Boolean.FALSE;
                }
                if (res instanceof Iterable<?> it) {
                    for (Object v : it) {
                        if (v != null && String.valueOf(v).toLowerCase(Locale.ROOT)
                                .contains(animation.toLowerCase(Locale.ROOT))) return Boolean.TRUE;
                    }
                    return Boolean.FALSE;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static boolean play(Entity mob, String animation, double lerpIn, double lerpOut,
                                double speed, boolean force, boolean quiet) {
        if (mob == null || animation == null) return false;
        Object model = MODELS.get(mob.getUniqueId());
        if (model == null && ensureAttached(mob, null)) model = MODELS.get(mob.getUniqueId());
        if (model == null) {
            if (!quiet) warnOnce("no-model", "анимация \"" + animation
                    + "\": на этой сущности нет нашей модели");
            return false;
        }
        try {
            Method getHandler = findMethod(model.getClass(), "getAnimationHandler", 0);
            if (getHandler == null) {
                warnOnce("no-handler-getter", "нет getAnimationHandler() — анимаций не будет");
                return false;
            }
            Object handler = getHandler.invoke(model);
            if (handler == null) {
                warnOnce("null-handler", "getAnimationHandler() вернул null — анимаций не будет");
                return false;
            }
            for (int argc = 5; argc >= 1; argc--) {
                Method p = findMethod(handler.getClass(), "playAnimation", argc);
                if (p == null) continue;
                Object[] args = buildArgs(p.getParameterTypes(), animation,
                        new double[]{lerpIn, lerpOut, speed}, force);
                if (args == null) continue;
                Object res = p.invoke(handler, args);
                if (res instanceof Boolean ok && !ok) {
                    if (!quiet) warnOnce("refused-" + animation, "ME отказался играть \""
                            + animation + "\" (force=" + force + ") — сверь имена анимаций блюпринта");
                    return false;
                }
                return true;
            }
            warnOnce("no-play-method", "не нашлось playAnimation — версия ME не поддерживается");
            return false;
        } catch (Throwable t) {
            warnOnce("throw-" + animation, "анимация \"" + animation + "\" не проигралась: " + rootCause(t));
            return false;
        }
    }

    // ── рефлексивная мелочь ───────────────────────────────────────────────

    private static Method findStatic(Class<?> owner, String name, int argc) {
        Method m = findMethod(owner, name, argc);
        return (m != null && java.lang.reflect.Modifier.isStatic(m.getModifiers())) ? m : null;
    }

    private static Method findMethod(Class<?> owner, String name, int argc) {
        Method best = null;
        for (Method m : owner.getMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != argc) continue;
            if (best == null || score(m.getParameterTypes()) < score(best.getParameterTypes())) best = m;
        }
        if (best != null) {
            try { best.setAccessible(true); } catch (Throwable ignored) {}
        }
        return best;
    }

    private static int score(Class<?>[] types) {
        int s = 0;
        for (Class<?> t : types) {
            if (t == String.class || t.isPrimitive()) continue;
            s += t.getName().startsWith("com.ticxo") ? 2 : 1;
        }
        return s;
    }

    private static Object[] buildArgs(Class<?>[] types, String text, double[] nums, boolean flag) {
        Object[] out = new Object[types.length];
        int ni = 0;
        for (int i = 0; i < types.length; i++) {
            Class<?> t = types[i];
            if (t == String.class) {
                if (text == null) return null;
                out[i] = text;
            } else if (t == boolean.class || t == Boolean.class) {
                out[i] = flag;
            } else if (t == double.class || t == Double.class) {
                out[i] = ni < nums.length ? nums[ni++] : 1.0;
            } else if (t == float.class || t == Float.class) {
                out[i] = (float) (ni < nums.length ? nums[ni++] : 1.0);
            } else if (t == int.class || t == Integer.class) {
                out[i] = (int) Math.round(ni < nums.length ? nums[ni++] : 1.0);
            } else {
                return null;
            }
        }
        return out;
    }

    private static Object[] buildAddModelArgs(Class<?>[] types, Object obj, boolean flag) {
        Object[] out = new Object[types.length];
        boolean objUsed = false;
        for (int i = 0; i < types.length; i++) {
            Class<?> t = types[i];
            if (t == boolean.class || t == Boolean.class) {
                out[i] = flag;
            } else if (!objUsed) {
                out[i] = obj;
                objUsed = true;
            } else {
                out[i] = null;
            }
        }
        return out;
    }

    private static Object unwrap(Object value) {
        if (value instanceof Optional<?> opt) return opt.orElse(null);
        return value;
    }

    private static String rootCause(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) c = c.getCause();
        return c.getClass().getSimpleName() + ": " + c.getMessage();
    }
}
