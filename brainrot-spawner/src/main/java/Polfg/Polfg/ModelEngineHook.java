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
 * Мостик к ModelEngine R4 целиком на рефлексии.
 *
 * Почему рефлексия, а не зависимость в pom.xml: ME лежит в приватном репозитории
 * Lumine, до которого GitHub Actions не достаёт, а в этом репозитории и так все
 * межплагинные вызовы сделаны через Bukkit.getPluginManager() + рефлексию.
 * Плюс так один и тот же jar работает и с ME, и без него: нет плагина — мобы
 * просто остаются ванильными.
 *
 * Сигнатуры между R4.0.x и R4.1.0 местами разъехались (где-то double, где-то
 * float, addModel то возвращает Optional, то сам ActiveModel), поэтому методы
 * ищутся по имени и числу аргументов, а типы подставляются под то, что реально
 * объявлено в найденном методе.
 */
public final class ModelEngineHook {

    private static final String API_CLASS = "com.ticxo.modelengine.api.ModelEngineAPI";
    private static final String LOG = "[BRAINROT/ME] ";

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
    /**
     * UUID сущности → id блюпринта. Нужен, чтобы навесить модель заново, если ME
     * её потерял (выгрузка чанка, /meg reload, перезапуск самого ME). Именно из-за
     * отсутствия этой памяти анимация «то работает, то нет»: модель отваливалась,
     * а код продолжал звать playAnimation в пустоту.
     */
    private static final Map<UUID, String> BLUEPRINTS = new ConcurrentHashMap<>();
    /** Сколько раз мы уже пытались перецепить модель. Три промаха — сдаёмся молча. */
    private static final Map<UUID, Integer> REATTACH_TRIES = new ConcurrentHashMap<>();

    /**
     * Ключи уже показанных однократных сообщений. Анимации дёргаются из тикающих
     * задач, поэтому одна и та же жалоба иначе залила бы консоль десятками строк
     * в секунду. Тут же лежат ключи разово печатаемых сигнатур API.
     */
    private static final java.util.Set<String> WARNED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private ModelEngineHook() {}

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
                Bukkit.getLogger().info(LOG + "плагин ModelEngine не найден, модели отключены");
                return;
            }
            apiClass = Class.forName(API_CLASS);
            mCreateActiveModel = findStatic(apiClass, "createActiveModel", 1);
            mCreateModeledEntity = findStatic(apiClass, "createModeledEntity", 1);
            mRemoveModeledEntity = findStatic(apiClass, "removeModeledEntity", 1);
            // Между сборками ME зовётся то getModeledEntity, то getModeledEntities —
            // берём то, что нашлось: по нему проверяем, жив ли ещё наш риг.
            mGetModeledEntity = findStatic(apiClass, "getModeledEntity", 1);
            if (mCreateActiveModel == null || mCreateModeledEntity == null) {
                Bukkit.getLogger().warning(LOG + "ModelEngine есть, но API незнакомый "
                        + "(нет createActiveModel/createModeledEntity) — модели отключены");
                return;
            }
            available = true;
            Bukkit.getLogger().info(LOG + "ModelEngine подключён");
        } catch (ClassNotFoundException e) {
            Bukkit.getLogger().warning(LOG + "класс " + API_CLASS + " не найден — нужен ModelEngine R4");
        } catch (Throwable t) {
            Bukkit.getLogger().warning(LOG + "не удалось подключиться к ModelEngine: " + t);
        }
    }

    // ── навесить и снять модель ───────────────────────────────────────────

    /**
     * Навешивает блюпринт на сущность и прячет её саму.
     *
     * @param blueprint id блюпринта = имя файла в plugins/ModelEngine/blueprints
     *                  без расширения, например "samovarus_maximus"
     * @return true, если модель реально навесилась
     */
    public static boolean attach(Entity mob, String blueprint) {
        if (mob == null || blueprint == null || blueprint.isEmpty()) return false;
        if (!isAvailable()) return false;
        UUID id = mob.getUniqueId();
        try {
            Object model = mCreateActiveModel.invoke(null, blueprint.toLowerCase(Locale.ROOT));
            if (model == null) {
                Bukkit.getLogger().warning(LOG + "блюпринт \"" + blueprint + "\" не загружен — "
                        + "проверь plugins/ModelEngine/blueprints и сделай /meg reload");
                return false;
            }
            Object rig = mCreateModeledEntity.invoke(null, mob);
            if (rig == null) {
                Bukkit.getLogger().warning(LOG + "createModeledEntity вернул null для " + mob.getType());
                return false;
            }

            // addModel(model, true) — второй аргумент «дублировать на клиентов сразу».
            // В части сборок метод возвращает Optional<ActiveModel> или сам ActiveModel:
            // если вернул живой объект, дальше анимации надо крутить именно на нём.
            Object attached = null;
            Method add2 = findMethod(rig.getClass(), "addModel", 2);
            if (add2 != null) {
                attached = add2.invoke(rig, buildAddModelArgs(add2.getParameterTypes(), model, true));
            } else {
                Method add1 = findMethod(rig.getClass(), "addModel", 1);
                if (add1 == null) {
                    Bukkit.getLogger().warning(LOG + "у ModeledEntity нет addModel — версия ME не поддерживается");
                    return false;
                }
                attached = add1.invoke(rig, model);
            }
            Object live = unwrap(attached);
            if (live != null && live.getClass() == model.getClass()) model = live;

            // Авторитетно спрашиваем риг, какой объект модели он у себя оставил.
            // addModel в части сборок клонирует ActiveModel, и тогда наш экземпляр
            // становится «сиротой»: анимации на нём проигрываются в никуда — ещё
            // одна причина тихо пропадающей походки.
            Object owned = modelFromRig(rig, blueprint);
            if (owned != null) model = owned;

            // Ванильную сущность-подложку убираем с глаз: она нужна только как
            // якорь позиции и хитбокс под клики игрока.
            Method vis = findMethod(rig.getClass(), "setBaseEntityVisible", 1);
            if (vis != null) vis.invoke(rig, false);

            MODELS.put(id, model);
            RIGS.put(id, rig);
            BLUEPRINTS.put(id, blueprint.toLowerCase(Locale.ROOT));
            REATTACH_TRIES.remove(id);
            return true;
        } catch (Throwable t) {
            Bukkit.getLogger().warning(LOG + "не удалось навесить \"" + blueprint + "\": " + rootCause(t));
            MODELS.remove(id);
            RIGS.remove(id);
            return false;
        }
    }

    /**
     * Достаёт из рига тот экземпляр ActiveModel, который он реально держит.
     * Путей несколько: getModel(id) отдаёт Optional, getModels() — Map.
     */
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
     * Проверяет, что модель на сущности всё ещё жива, и при необходимости
     * навешивает её заново по запомненному блюпринту.
     *
     * Ровно это и лечит «анимация запускается через раз»: ME теряет рига при
     * выгрузке чанка и при собственном reload, наш кэш об этом не узнаёт, и все
     * последующие playAnimation тихо уходят в никуда до перезапуска сервера.
     *
     * @return true, если после вызова модель на сущности есть
     */
    public static boolean ensureAttached(Entity mob) {
        if (mob == null || !mob.isValid() || mob.isDead()) return false;
        if (!isAvailable()) return false;
        UUID id = mob.getUniqueId();
        String blueprint = BLUEPRINTS.get(id);
        if (blueprint == null) return MODELS.containsKey(id);
        if (rigAlive(id) && MODELS.get(id) != null) {
            REATTACH_TRIES.remove(id);
            return true;
        }
        int tries = REATTACH_TRIES.getOrDefault(id, 0);
        if (tries >= 3) return false;
        REATTACH_TRIES.put(id, tries + 1);
        // Старый риг мог остаться висеть половинчато — сносим и строим заново.
        Object stale = RIGS.remove(id);
        MODELS.remove(id);
        if (stale != null) {
            try {
                Method destroy = findMethod(stale.getClass(), "destroy", 0);
                if (destroy != null) destroy.invoke(stale);
            } catch (Throwable ignored) {}
        }
        boolean ok = attach(mob, blueprint);
        if (ok) {
            Bukkit.getLogger().info(LOG + "модель \"" + blueprint + "\" навешена заново "
                    + "(ME потерял рига, попытка " + (tries + 1) + ")");
        } else if (tries + 1 >= 3) {
            warnOnce("reattach-fail-" + blueprint, "не смог вернуть модель \"" + blueprint
                    + "\" после трёх попыток — дальше моб останется ванильным");
        }
        return ok;
    }

    /** Жив ли ещё наш риг с точки зрения самого ModelEngine. */
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
                Class<?> want = mGetModeledEntity.getParameterTypes()[0];
                Object arg = (want == UUID.class) ? id : null;
                if (arg != null) {
                    Object live = unwrap(mGetModeledEntity.invoke(null, arg));
                    // ME знает про сущность — риг на месте. Не знает — потеряли.
                    return live != null;
                }
            } catch (Throwable ignored) {}
        }
        return true;
    }

    /** Снимает модель. Дёргать обязательно, иначе ME оставит висеть рига без хозяина. */
    public static void detach(Entity mob) {
        if (mob == null) return;
        UUID id = mob.getUniqueId();
        Object rig = RIGS.remove(id);
        MODELS.remove(id);
        // Блюпринт забываем здесь же: иначе ensureAttached упорно возвращал бы
        // модель на моба, которого мы только что осознанно почистили.
        BLUEPRINTS.remove(id);
        REATTACH_TRIES.remove(id);
        if (rig == null) return;
        try {
            Method destroy = findMethod(rig.getClass(), "destroy", 0);
            if (destroy != null) {
                destroy.invoke(rig);
                return;
            }
            if (mRemoveModeledEntity != null) mRemoveModeledEntity.invoke(null, id);
        } catch (Throwable t) {
            Bukkit.getLogger().warning(LOG + "не удалось снять модель: " + rootCause(t));
        }
    }

    /**
     * Есть ли на этой сущности наша модель. Помнить блюпринт достаточно: сам
     * объект модели ME может у нас из-под рук потерять, а тикающие задачи должны
     * продолжать заходить сюда, иначе восстанавливать станет некому.
     */
    public static boolean hasModel(Entity mob) {
        if (mob == null) return false;
        UUID id = mob.getUniqueId();
        return MODELS.containsKey(id) || BLUEPRINTS.containsKey(id);
    }

    // ── анимации ──────────────────────────────────────────────────────────

    /** Плавно, с обычной скоростью, не перебивая то же самое, если уже играет. */
    public static boolean play(Entity mob, String animation) {
        return play(mob, animation, 0.2, 0.2, 1.0, false);
    }

    /** Перебить текущую анимацию этой (для hurt/death, где ждать нельзя). */
    public static boolean playForced(Entity mob, String animation) {
        return play(mob, animation, 0.0, 0.2, 1.0, true);
    }

    /**
     * Вернуться в зацикленную анимацию после одноразовой. Именно force=true:
     * пока предыдущая once-анимация не отпустила кости, ME мягкий запрос
     * игнорирует, и моб застывает столбом до конца жизни.
     */
    public static boolean loop(Entity mob, String animation) {
        return play(mob, animation, 0.2, 0.2, 1.0, true);
    }

    /**
     * Страховка зацикленной анимации. Сначала убеждаемся, что модель вообще на
     * месте (её могло унести выгрузкой чанка), потом спрашиваем ME, играет ли
     * сейчас нужный цикл. Если точно не играет — поднимаем с force, иначе не
     * трогаем: перезапуск идущей анимации виден как дёрганый кадр.
     *
     * Раньше здесь был слепой мягкий запрос, и когда ME считал цикл идущим, а на
     * экране моб стоял столбом, вернуть походку могла только перезагрузка сервера.
     */
    public static boolean keepAlive(Entity mob, String animation) {
        if (mob == null || animation == null) return false;
        if (!ensureAttached(mob)) return false;
        Boolean playing = isPlaying(mob, animation);
        if (Boolean.TRUE.equals(playing)) return true;
        boolean force = Boolean.FALSE.equals(playing);
        return play(mob, animation, 0.2, 0.2, 1.0, force, true);
    }

    /**
     * Играет ли сейчас именно эта анимация. null — ME не умеет отвечать на такой
     * вопрос в этой сборке, тогда решение принимается по старой мягкой схеме.
     */
    public static Boolean isPlaying(Entity mob, String animation) {
        if (mob == null || animation == null) return null;
        Object model = MODELS.get(mob.getUniqueId());
        if (model == null) return Boolean.FALSE;
        try {
            Method getHandler = findMethod(model.getClass(), "getAnimationHandler", 0);
            if (getHandler == null) return null;
            Object handler = getHandler.invoke(model);
            if (handler == null) return null;
            // Прямой вопрос «играешь ли X».
            for (String name : new String[]{"isPlayingAnimation", "isPlaying"}) {
                Method m = findMethod(handler.getClass(), name, 1);
                if (m != null && m.getParameterTypes()[0] == String.class) {
                    Object res = m.invoke(handler, animation);
                    if (res instanceof Boolean b) return b;
                }
            }
            // Иначе смотрим список того, что крутится прямо сейчас.
            for (String name : new String[]{"getPlayingAnimations", "getAnimations", "getAnimation"}) {
                Method m = findMethod(handler.getClass(), name, 0);
                if (m == null) continue;
                Object res = m.invoke(handler);
                if (res == null) continue;
                if (res instanceof Map<?, ?> map) {
                    if (map.isEmpty()) return Boolean.FALSE;
                    for (Object k : map.keySet()) {
                        if (String.valueOf(k).equalsIgnoreCase(animation)) return Boolean.TRUE;
                    }
                    return Boolean.FALSE;
                }
                if (res instanceof Iterable<?> it) {
                    boolean any = false;
                    for (Object v : it) {
                        any = true;
                        if (v != null && String.valueOf(v).toLowerCase(Locale.ROOT)
                                .contains(animation.toLowerCase(Locale.ROOT))) return Boolean.TRUE;
                    }
                    return any ? Boolean.FALSE : Boolean.FALSE;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * @param lerpIn  секунды плавного входа
     * @param lerpOut секунды плавного выхода
     * @param speed   1.0 — как в блокбенче
     * @param force   перебить уже играющую анимацию с тем же именем
     */
    public static boolean play(Entity mob, String animation, double lerpIn, double lerpOut,
                               double speed, boolean force) {
        return play(mob, animation, lerpIn, lerpOut, speed, force, false);
    }

    private static boolean play(Entity mob, String animation, double lerpIn, double lerpOut,
                                double speed, boolean force, boolean quiet) {
        if (mob == null || animation == null) return false;
        Object model = MODELS.get(mob.getUniqueId());
        if (model == null) {
            // Модель могло унести выгрузкой чанка или /meg reload. Если блюпринт
            // помним — возвращаем её на место и играем как ни в чём не бывало.
            if (ensureAttached(mob)) model = MODELS.get(mob.getUniqueId());
        }
        if (model == null) {
            if (!quiet) warnOnce("no-model", "анимация \"" + animation + "\": на этой сущности нет нашей "
                    + "модели — attach не сработал либо моб уже почищен");
            return false;
        }
        try {
            Method getHandler = findMethod(model.getClass(), "getAnimationHandler", 0);
            if (getHandler == null) {
                warnOnce("no-handler-getter", "у " + model.getClass().getName()
                        + " нет getAnimationHandler() — API ModelEngine незнакомый, анимаций не будет");
                return false;
            }
            Object handler = getHandler.invoke(model);
            if (handler == null) {
                warnOnce("null-handler", "getAnimationHandler() вернул null — анимаций не будет");
                return false;
            }
            for (int argc = 5; argc >= 1; argc--) {
                Method play = findMethod(handler.getClass(), "playAnimation", argc);
                if (play == null) continue;
                Object[] args = buildArgs(play.getParameterTypes(), animation,
                        new double[]{lerpIn, lerpOut, speed}, force);
                if (args == null) continue;
                if (WARNED.add("sig-play-" + argc)) {
                    Bukkit.getLogger().info(LOG + "playAnimation: " + describeMethod(play)
                            + ", анимации блюпринта: " + animationNames(model));
                }
                Object res = play.invoke(handler, args);
                // ME возвращает boolean. false означает «не играю»: либо такого
                // имени нет в блюпринте, либо ровно эта же анимация уже идёт и
                // force не выставлен. Без проверки мы рапортовали бы успех при
                // полной тишине на экране — именно так и терялся walk.
                if (res instanceof Boolean ok && !ok) {
                    if (!quiet) warnOnce("refused-" + animation, "ME отказался играть \"" + animation
                            + "\" (force=" + force + "). Чаще всего такой анимации нет в блюпринте: "
                            + "сверь имена — сейчас ME видит " + animationNames(model));
                    return false;
                }
                return true;
            }
            warnOnce("no-play-method", "у " + handler.getClass().getName()
                    + " не нашлось подходящего playAnimation — версия ME не поддерживается");
            return false;
        } catch (Throwable t) {
            warnOnce("throw-" + animation,
                    "анимация \"" + animation + "\" не проигралась: " + rootCause(t));
            return false;
        }
    }

    public static void stop(Entity mob, String animation) {
        if (mob == null || animation == null) return;
        Object model = MODELS.get(mob.getUniqueId());
        if (model == null) return;
        try {
            Method getHandler = findMethod(model.getClass(), "getAnimationHandler", 0);
            if (getHandler == null) return;
            Object handler = getHandler.invoke(model);
            if (handler == null) return;
            Method stop = findMethod(handler.getClass(), "stopAnimation", 1);
            if (stop != null) stop.invoke(handler, animation);
        } catch (Throwable ignored) {
        }
    }

    // ── рефлексивная мелочь ───────────────────────────────────────────────

    private static String describeMethod(Method m) {
        StringBuilder sb = new StringBuilder(m.getDeclaringClass().getSimpleName())
                .append('.').append(m.getName()).append('(');
        Class<?>[] t = m.getParameterTypes();
        for (int i = 0; i < t.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(t[i].getSimpleName());
        }
        return sb.append(") → ").append(m.getReturnType().getSimpleName()).toString();
    }

    /**
     * Имена анимаций так, как их разобрал сам ME из нашего bbmodel. Самый ценный
     * диагноз: если тут пусто или нет walk — проблема в блюпринте, а не в коде.
     * Путь до них между версиями зовётся по-разному, поэтому пробуем варианты.
     */
    private static String animationNames(Object model) {
        try {
            Object bp = null;
            for (String getter : new String[]{"getBlueprint", "getBlueprintModel", "getModelBlueprint"}) {
                Method m = findMethod(model.getClass(), getter, 0);
                if (m != null) {
                    bp = m.invoke(model);
                    if (bp != null) break;
                }
            }
            if (bp == null) return "неизвестно (нет getBlueprint)";
            Method anims = findMethod(bp.getClass(), "getAnimations", 0);
            if (anims == null) return "неизвестно (нет getAnimations)";
            Object value = anims.invoke(bp);
            if (value instanceof Map<?, ?> map) {
                if (map.isEmpty()) return "СПИСОК ПУСТ";
                return String.join(", ", map.keySet().stream().map(String::valueOf).sorted().toList());
            }
            return String.valueOf(value);
        } catch (Throwable t) {
            return "не удалось прочитать (" + rootCause(t) + ")";
        }
    }

    /**
     * Полный отчёт по одной сущности для команды /brainrotspawn megdebug.
     * Пишем именно так, подробно: до вики ModelEngine из песочницы не дотянуться,
     * поэтому единственный источник правды о сигнатурах — живой сервер.
     */
    public static java.util.List<String> describe(Entity mob) {
        java.util.List<String> out = new java.util.ArrayList<>();
        out.add("ModelEngine: " + (isAvailable() ? "подключён" : "НЕТ"));
        out.add("моделей на учёте: " + MODELS.size() + ", риг: " + RIGS.size());
        if (mob == null) {
            out.add("сущность не передана");
            return out;
        }
        Object model = MODELS.get(mob.getUniqueId());
        if (model == null) {
            out.add("на этой сущности нашей модели нет");
            return out;
        }
        out.add("ActiveModel: " + model.getClass().getName());
        out.add("анимации блюпринта: " + animationNames(model));
        try {
            Method getHandler = findMethod(model.getClass(), "getAnimationHandler", 0);
            if (getHandler == null) {
                out.add("getAnimationHandler(): НЕ НАЙДЕН");
                return out;
            }
            Object handler = getHandler.invoke(model);
            out.add("AnimationHandler: " + (handler == null ? "null" : handler.getClass().getName()));
            if (handler == null) return out;
            int found = 0;
            for (int argc = 1; argc <= 6; argc++) {
                Method m = findMethod(handler.getClass(), "playAnimation", argc);
                if (m == null) continue;
                found++;
                Object[] args = buildArgs(m.getParameterTypes(), "walk", new double[]{0.2, 0.2, 1.0}, true);
                out.add("  " + describeMethod(m) + (args == null ? "  §7(не подставить аргументы)" : "  §aподходит"));
            }
            if (found == 0) out.add("  playAnimation НЕ НАЙДЕН НИ В ОДНОЙ ФОРМЕ");
            // Вики ME из песочницы недоступна, поэтому список методов хендлера —
            // единственный способ узнать, умеет ли он отвечать на вопрос «что играется
            // прямо сейчас». Найдётся такой метод — слепую мягкую страховку walk можно
            // будет заменить точной проверкой «цикл жив / цикл оборвался».
            java.util.TreeSet<String> names = new java.util.TreeSet<>();
            for (Method m : handler.getClass().getMethods()) {
                switch (m.getName()) {
                    case "wait", "notify", "notifyAll", "equals", "hashCode",
                         "toString", "getClass" -> { }
                    default -> names.add(m.getName() + "/" + m.getParameterCount());
                }
            }
            StringBuilder line = new StringBuilder();
            int inLine = 0;
            for (String n : names) {
                if (inLine == 6) { out.add("методы: " + line); line.setLength(0); inLine = 0; }
                if (inLine > 0) line.append(", ");
                line.append(n);
                inLine++;
            }
            if (inLine > 0) out.add("методы: " + line);
        } catch (Throwable t) {
            out.add("ошибка разбора: " + rootCause(t));
        }
        return out;
    }

    private static Method findStatic(Class<?> owner, String name, int argc) {
        Method m = findMethod(owner, name, argc);
        return (m != null && java.lang.reflect.Modifier.isStatic(m.getModifiers())) ? m : null;
    }

    private static Method findMethod(Class<?> owner, String name, int argc) {
        Method best = null;
        for (Method m : owner.getMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != argc) continue;
            // из перегрузок берём ту, у которой аргументы попроще: меньше шансов
            // напороться на вариант с внутренними типами ME
            if (best == null || simpler(m.getParameterTypes(), best.getParameterTypes())) best = m;
        }
        if (best != null) {
            try { best.setAccessible(true); } catch (Throwable ignored) {}
        }
        return best;
    }

    private static boolean simpler(Class<?>[] a, Class<?>[] b) {
        return score(a) < score(b);
    }

    private static int score(Class<?>[] types) {
        int s = 0;
        for (Class<?> t : types) {
            if (t == String.class || t.isPrimitive()) continue;
            s += t.getName().startsWith("com.ticxo") ? 2 : 1;
        }
        return s;
    }

    /**
     * Раскладывает наши значения по объявленным типам аргументов: строка идёт в
     * String, флаг — в boolean, числа — по порядку в любые числовые слоты, всё
     * прочее (объекты вроде ActiveModel) берётся из extras.
     * Возвращает null, если под какой-то аргумент нечего подставить.
     */
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

    /** Аргументы для addModel: объект модели + флаг, порядок берём из объявления. */
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
