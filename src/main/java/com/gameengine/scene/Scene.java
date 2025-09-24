package com.gameengine.scene;

import com.gameengine.core.GameObject;
import com.gameengine.core.Component;
import com.gameengine.components.TransformComponent;
import com.gameengine.components.PhysicsComponent;
import com.gameengine.input.InputManager;
import com.gameengine.math.Vector2;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 场景类，管理游戏对象和组件
 */
public class Scene {
    private String name;
    private List<GameObject> gameObjects;
    private List<GameObject> objectsToAdd;
    private List<GameObject> objectsToRemove;
    private boolean initialized;
    private final Map<Class<? extends Component<?>>, List<GameObject>> componentIndex;
    
    public Scene(String name) {
        this.name = name;
        this.gameObjects = new ArrayList<>();
        this.objectsToAdd = new ArrayList<>();
        this.objectsToRemove = new ArrayList<>();
        this.initialized = false;
        this.componentIndex = new HashMap<>();
    }
    
    /**
     * 初始化场景
     */
    public void initialize() {
        for (GameObject obj : gameObjects) {
            obj.initialize();
        }
        initialized = true;
    }
    
    /**
     * 更新场景
     */
    public void update(float deltaTime) {
        // 添加新对象
        for (GameObject obj : objectsToAdd) {
            gameObjects.add(obj);
            if (initialized) {
                obj.initialize();
            }
        }
        objectsToAdd.clear();
        
        // 移除标记的对象
        for (GameObject obj : objectsToRemove) {
            gameObjects.remove(obj);
        }
        objectsToRemove.clear();
        
        // 更新所有活跃的游戏对象
        Iterator<GameObject> iterator = gameObjects.iterator();
        while (iterator.hasNext()) {
            GameObject obj = iterator.next();
            if (obj.isActive()) {
                obj.update(deltaTime);
            } else {
                iterator.remove();
            }
        }
    }
    
    /**
     * 渲染场景
     */
    public void render() {
        for (GameObject obj : gameObjects) {
            if (obj.isActive()) {
                obj.render();
            }
        }
    }
    
    /**
     * 添加游戏对象到场景
     */
    public void addGameObject(GameObject gameObject) {
        objectsToAdd.add(gameObject);
    }
    
    /**
     * 根据组件类型查找游戏对象
     */
    public <T extends Component<T>> List<GameObject> findGameObjectsByComponent(Class<T> componentType) {
        return componentIndex.computeIfAbsent(componentType, k -> 
            gameObjects.stream()
                .filter(obj -> obj.hasComponent(componentType))
                .collect(Collectors.toList())
        );
    }
    
    /**
     * 获取所有具有指定组件的游戏对象
     */
    public <T extends Component<T>> List<T> getComponents(Class<T> componentType) {
        return findGameObjectsByComponent(componentType).stream()
            .map(obj -> obj.getComponent(componentType))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
    
    /**
     * 清空场景
     */
    public void clear() {
        gameObjects.clear();
        objectsToAdd.clear();
        objectsToRemove.clear();
    }
    
    /**
     * 获取场景名称
     */
    public String getName() {
        return name;
    }
    
    /**
     * 获取所有游戏对象
     */
    public List<GameObject> getGameObjects() {
        return new ArrayList<>(gameObjects);
    }
    
    /**
     * 处理玩家输入 - 可被子类重写
     */
    protected void handlePlayerInput() {
        List<GameObject> players = findGameObjectsByComponent(TransformComponent.class);
        if (players.isEmpty()) return;
        
        GameObject player = players.get(0);
        TransformComponent transform = player.getComponent(TransformComponent.class);
        PhysicsComponent physics = player.getComponent(PhysicsComponent.class);
        
        if (transform == null || physics == null) return;
        
        InputManager inputManager = InputManager.getInstance();
        Vector2 movement = new Vector2();
        
        if (inputManager.isKeyPressed(87) || inputManager.isKeyPressed(38)) { // W或上箭头
            movement.y -= 1;
        }
        if (inputManager.isKeyPressed(83) || inputManager.isKeyPressed(40)) { // S或下箭头
            movement.y += 1;
        }
        if (inputManager.isKeyPressed(65) || inputManager.isKeyPressed(37)) { // A或左箭头
            movement.x -= 1;
        }
        if (inputManager.isKeyPressed(68) || inputManager.isKeyPressed(39)) { // D或右箭头
            movement.x += 1;
        }
        
        if (movement.magnitude() > 0) {
            movement = movement.normalize().multiply(200);
            physics.setVelocity(movement);
        }
        
        // 边界检查
        Vector2 pos = transform.getPosition();
        if (pos.x < 0) pos.x = 0;
        if (pos.y < 0) pos.y = 0;
        if (pos.x > 800 - 20) pos.x = 800 - 20;
        if (pos.y > 600 - 20) pos.y = 600 - 20;
        transform.setPosition(pos);
    }
    
    /**
     * 更新物理系统 - 可被子类重写
     */
    protected void updatePhysics() {
        List<PhysicsComponent> physicsComponents = getComponents(PhysicsComponent.class);
        for (PhysicsComponent physics : physicsComponents) {
            // 边界反弹
            TransformComponent transform = physics.getOwner().getComponent(TransformComponent.class);
            if (transform != null) {
                Vector2 pos = transform.getPosition();
                Vector2 velocity = physics.getVelocity();
                
                if (pos.x <= 0 || pos.x >= 800 - 15) {
                    velocity.x = -velocity.x;
                    physics.setVelocity(velocity);
                }
                if (pos.y <= 0 || pos.y >= 600 - 15) {
                    velocity.y = -velocity.y;
                    physics.setVelocity(velocity);
                }
                
                // 确保在边界内
                if (pos.x < 0) pos.x = 0;
                if (pos.y < 0) pos.y = 0;
                if (pos.x > 800 - 15) pos.x = 800 - 15;
                if (pos.y > 600 - 15) pos.y = 600 - 15;
                transform.setPosition(pos);
            }
        }
    }
    
    /**
     * 检查碰撞 - 可被子类重写
     */
    protected void checkCollisions() {
        // 直接查找玩家对象
        List<GameObject> players = findGameObjectsByComponent(TransformComponent.class);
        if (players.isEmpty()) return;
        
        GameObject player = players.get(0);
        TransformComponent playerTransform = player.getComponent(TransformComponent.class);
        if (playerTransform == null) return;
        
        // 直接查找所有游戏对象，然后过滤出敌人
        for (GameObject obj : getGameObjects()) {
            if (obj.getName().equals("Enemy")) {
                TransformComponent enemyTransform = obj.getComponent(TransformComponent.class);
                if (enemyTransform != null) {
                    float distance = playerTransform.getPosition().distance(enemyTransform.getPosition());
                    if (distance < 25) {
                        // 碰撞！重置玩家位置
                        playerTransform.setPosition(new Vector2(400, 300));
                        break;
                    }
                }
            }
        }
    }
}